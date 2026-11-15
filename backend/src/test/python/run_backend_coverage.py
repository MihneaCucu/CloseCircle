"""Measure a trusted backend checkout without changing its POM or test harness.

Reports include every compiled application class, including uncovered classes.
The HTTP suites use the existing disposable local PostgreSQL harnesses.
"""
import argparse
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
JACOCO = '0.8.14'
SUITES = ('check_latest_locations.py', 'check_location_freshness.py',
          'check_location_accuracy.py', 'check_location_recovery.py', 'check_location_upload.py',
          'check_group_sharing.py', 'check_sharing_revocation.py',
          'check_temporary_sharing.py', 'check_shared_groups.py')


def revision(path):
    return subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()


def counters(report):
    return {item.get('type'): {'missed': int(item.get('missed')), 'covered': int(item.get('covered'))}
            for item in ET.parse(report).getroot().findall('counter')}


def percentage(counter):
    covered, missed = counter['covered'], counter['missed']
    return f'{100 * covered / (covered + missed):.1f}%' if covered + missed else 'n/a'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-root', required=True, type=Path,
                        help='Trusted backend checkout with location unit tests and PostgreSQL harnesses')
    parser.add_argument('--output', type=Path, default=ROOT / 'backend/target/location-coverage',
                        help='Fresh report directory (existing nonempty directories are rejected)')
    args = parser.parse_args()
    backend, output = args.backend_root.resolve(), args.output.resolve()
    if output.exists() and any(output.iterdir()):
        parser.error('--output must be absent or empty; use a fresh directory for each measurement')
    if not (backend / 'pom.xml').is_file() or not all(
            (backend / 'src/test/python' / suite).is_file() for suite in SUITES):
        parser.error('Backend checkout does not contain the expected location test suites')
    java = shutil.which('java')
    if java is None:
        parser.error('Java is required')
    # Agent options use commas as separators and Surefire parses argLine itself.
    # Use a simple report path rather than silently collecting incomplete data.
    if any(char.isspace() or char in ',=' for char in str(output)):
        parser.error('--output must not contain whitespace, commas or equals signs')
    if output == backend or backend.is_relative_to(output):
        parser.error('--output must not contain the backend source directory')
    mvn = ['mvn', '-B', '-ntp', '-f', str(backend / 'pom.xml')]
    subprocess.run([*mvn, 'clean'], check=True)
    output.mkdir(parents=True, exist_ok=True)
    tools = output / 'tools'
    manifest = {'backendCommit': revision(backend), 'contractCommit': revision(ROOT),
                'contractDirty': bool(subprocess.check_output(
                    ['git', '-C', str(ROOT), 'status', '--porcelain'], text=True).strip()),
                'backendDirty': bool(subprocess.check_output(
                    ['git', '-C', str(backend), 'status', '--porcelain'], text=True).strip()),
                'jacocoVersion': JACOCO, 'complete': False, 'checks': [], 'reports': {}}

    def run(name, command, env=None):
        result = subprocess.run(command, env=env)
        manifest['checks'].append({'name': name, 'exitCode': result.returncode})
        result.check_returncode()

    def report(name, data_files):
        folder = output / name
        folder.mkdir(exist_ok=True)
        subprocess.run([java, '-jar', str(cli), 'report', *map(str, data_files),
                        '--classfiles', str(backend / 'target/classes'),
                        '--sourcefiles', str(backend / 'src/main/java'),
                        '--html', str(folder), '--xml', str(folder / 'jacoco.xml'),
                        '--csv', str(folder / 'jacoco.csv'), '--name', f'CloseCircle {name}'], check=True)
        measured = counters(folder / 'jacoco.xml')
        if measured.get('LINE', {}).get('covered', 0) == 0:
            raise RuntimeError('No application lines were recorded for ' + name)
        manifest['reports'][name] = measured
        if name == 'http-postgres':
            covered_classes = {item.get('name') for item in ET.parse(folder / 'jacoco.xml').getroot().findall('package/class')
                               if any(c.get('type') == 'LINE' and int(c.get('covered')) > 0
                                      for c in item.findall('counter'))}
            required = {'com/closecircle/api/location/' + item for item in (
                'LatestLocationController', 'LatestLocationService', 'LocationUploadController', 'LocationUploadService')}
            required |= {'com/closecircle/api/sharing/SharingController', 'com/closecircle/api/sharing/SharingService'}
            if not required.issubset(covered_classes):
                raise RuntimeError('HTTP execution did not cover the location read/upload or sharing paths')

    cli = tools / f'org.jacoco.cli-{JACOCO}-nodeps.jar'
    agent = tools / f'org.jacoco.agent-{JACOCO}-runtime.jar'
    unit, http = output / 'unit.exec', output / 'http-postgres.exec'
    failed = False
    try:
        for artifact in ('agent:runtime', 'cli:nodeps'):
            component, classifier = artifact.split(':')
            run('resolve-jacoco-' + component, [*mvn,
                'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy',
                f'-Dartifact=org.jacoco:org.jacoco.{component}:{JACOCO}:jar:{classifier}',
                '-DoutputDirectory=' + str(tools)])
        run('unit-tests', [*mvn, '-DargLine=' + shlex.quote(
            f'-javaagent:{agent}=destfile={unit},append=false,includes=com.closecircle.*'), 'verify'])
        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        for path in (backend / 'target/surefire-reports').glob('TEST-*.xml'):
            suite = ET.parse(path).getroot()
            for key in totals:
                totals[key] += int(suite.get(key, '0'))
        manifest['unitTests'] = totals
        if totals['tests'] - totals['skipped'] <= 0 or not unit.is_file():
            raise RuntimeError('No unit test execution data was collected')

        # Existing harnesses deliberately sanitize environment variables. A local
        # java launcher on PATH instruments their API JVMs while preserving that
        # safeguard, including across API/database restarts. No source is patched.
        with tempfile.TemporaryDirectory(prefix='cc-coverage-java-') as temp:
            wrapper = Path(temp) / 'java'
            option = f'-javaagent:{agent}=destfile={http},append=true,includes=com.closecircle.*'
            wrapper.write_text('#!/bin/sh\nexec ' + shlex.quote(java) + ' ' + shlex.quote(option) + ' "$@"\n')
            wrapper.chmod(0o700)
            env = dict(os.environ, PATH=temp + os.pathsep + os.environ['PATH'])
            for suite in SUITES:
                run(suite, [sys.executable, str(backend / 'src/test/python' / suite)], env)
            run('real-api-contract', [sys.executable, str(Path(__file__).with_name(
                'check_location_contract_http.py')), '--backend-root', str(backend)], env)
        if not http.is_file():
            raise RuntimeError('No HTTP/PostgreSQL execution data was collected')
        manifest['complete'] = True
    except (subprocess.CalledProcessError, RuntimeError) as error:
        print('FAIL: backend validation did not complete (' + type(error).__name__ + ')', file=sys.stderr)
        failed = True
    finally:
        if cli.is_file():
            for name, files in [('unit', [unit]), ('http-postgres', [http]), ('combined', [unit, http])]:
                if all(path.is_file() for path in files):
                    try:
                        report(name, files)
                    except (subprocess.CalledProcessError, RuntimeError):
                        manifest['complete'] = False
                        failed = True
        if set(manifest['reports']) != {'unit', 'http-postgres', 'combined'}:
            manifest['complete'] = False
            failed = True
        manifest['complete'] = manifest['complete'] and not failed
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        lines = ['# Backend location coverage', '',
                 '**Complete passing run**' if manifest['complete'] else '**INCOMPLETE / FAILED RUN: partial reports only**',
                 '', 'Backend commit: `' + manifest['backendCommit'] + '`',
                 'Contract/tooling commit: `' + manifest['contractCommit'] + '`',
                 'Backend has local tracked/untracked changes: `' + str(manifest['backendDirty']) + '`',
                 'Contract/tooling has local changes: `' + str(manifest['contractDirty']) + '`',
                 '', '| Test scope | Lines | Branches |', '| --- | --- | --- |']
        for name, measured in manifest['reports'].items():
            lines.append('| ' + name + ' | ' + percentage(measured['LINE']) + ' | ' + percentage(measured['BRANCH']) + ' |')
        lines += ['', 'Denominator: all compiled backend application classes, including uncovered classes. '
                  'Unit and HTTP/PostgreSQL reports are separate; combined coverage is their union, not their average. '
                  'These measurements do not prove Android behavior, whole-story acceptance or production integration. '
                  'No percentage gate is claimed or imposed.', '']
        (output / 'summary.md').write_text('\n'.join(lines))
        print('\n'.join(lines))
        # Agents/tools and launcher are implementation details, not report evidence.
        if tools.exists():
            shutil.rmtree(tools)
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
