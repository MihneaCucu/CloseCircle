"""Run against a disposable local PostgreSQL cluster; never connects to Neon.

From backend/: mvn -q -DskipTests package && python3 src/test/python/check_latest_locations.py
Requires Java, Python 3, initdb, pg_ctl, createdb and psql on PATH.
"""
import base64
import getpass
import hashlib
import hmac
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b'=').decode()


def main():
    if not __debug__:
        raise SystemExit('Run without Python optimization so assertions are enabled.')
    backend = Path(__file__).resolve().parents[3]
    jar = backend / 'target' / 'closecircle-api.jar'
    if not jar.exists():
        raise SystemExit('Build the backend jar first; see the module docstring.')
    pg_port, api_port = free_port(), free_port()
    while api_port == pg_port:
        api_port = free_port()
    secret = 'synthetic-local-test-secret-32-bytes-only'
    # Do not inherit cloud credentials, Postgres service settings, Spring overrides,
    # Java options, or other application configuration from the caller.
    safe_env = {key: os.environ[key] for key in ('PATH', 'HOME', 'USER', 'LOGNAME', 'JAVA_HOME', 'TMPDIR')
                if key in os.environ}
    safe_env['LC_ALL'] = 'C'
    with tempfile.TemporaryDirectory(prefix='cc-location-test-') as temp:
        data = Path(temp) / 'pg'
        server_started = False
        app = None
        try:
            subprocess.run(['initdb', '-D', str(data), '-A', 'trust', '--no-locale'],
                           check=True, stdout=subprocess.DEVNULL, env=safe_env)
            subprocess.run(['pg_ctl', '-D', str(data), '-l', str(Path(temp) / 'pg.log'),
                            '-o', f'-p {pg_port} -h 127.0.0.1 -k {temp}', '-w', 'start'],
                           check=True, stdout=subprocess.DEVNULL, env=safe_env)
            server_started = True
            subprocess.run(['createdb', '-h', '127.0.0.1', '-p', str(pg_port),
                            'closecircle_test_scrum19'], check=True, env=safe_env)
            env = dict(safe_env, DATABASE_URL=f'jdbc:postgresql://127.0.0.1:{pg_port}/closecircle_test_scrum19?sslmode=disable',
                       DATABASE_USERNAME=getpass.getuser(), DATABASE_PASSWORD='', JWT_SECRET=secret,
                       PORT=str(api_port))
            with open(Path(temp) / 'app.log', 'w') as log:
                app = subprocess.Popen(['java', '-jar', str(jar)], env=env, stdout=log, stderr=subprocess.STDOUT)
                base = f'http://127.0.0.1:{api_port}'
                for _ in range(100):
                    if app.poll() is not None:
                        raise RuntimeError('Backend exited during startup: ' + (Path(temp) / 'app.log').read_text())
                    try:
                        urllib.request.urlopen(base + '/actuator/health', timeout=1).close()
                        break
                    except (OSError, urllib.error.URLError):
                        time.sleep(0.1)
                else:
                    raise RuntimeError('Backend did not start')

                db = ['psql', '-X', '-h', '127.0.0.1', '-p', str(pg_port), '-d', 'closecircle_test_scrum19',
                      '-v', 'ON_ERROR_STOP=1', '-q']
                def sql(text):
                    subprocess.run(db, input=text, text=True, check=True, stdout=subprocess.DEVNULL, env=safe_env)

                group = '10000000-0000-0000-0000-000000000001'
                other_group = '10000000-0000-0000-0000-000000000002'
                missing_group = '10000000-0000-0000-0000-000000000003'
                ids = [f'00000000-0000-0000-0000-{n:012d}' for n in range(1, 8)]
                sql(''.join(f"INSERT INTO users(user_id,email,password_hash,display_name) VALUES ('{u}','test{n}@example.invalid','x','User {n}');" for n, u in enumerate(ids, 1)))
                sql(f"INSERT INTO groups(group_id,name,created_by) VALUES ('{group}','Test','{ids[0]}');")
                sql(''.join(f"INSERT INTO group_members(group_id,user_id,joined_at) VALUES ('{group}','{u}','2026-01-01T00:00:00Z');" for u in ids[:6]))
                sql(f"INSERT INTO shares(group_id,user_id,sharing_enabled,share_live_location) VALUES ('{group}','{ids[1]}',true,true),('{group}','{ids[2]}',true,true),('{group}','{ids[3]}',false,true),('{group}','{ids[4]}',true,false);")
                sql(f"INSERT INTO locations(group_id,user_id,latitude,longitude,accuracy_m,recorded_at) VALUES ('{group}','{ids[1]}',1,2,NULL,'2026-01-01T00:00:00Z'),('{group}','{ids[1]}',3,4,6.5,'2026-01-02T00:00:00Z'),('{group}','{ids[3]}',9,9,NULL,'2026-01-02T00:00:00Z');")
                # User 2 shares different coordinates in both groups; user 4
                # shares only in the second group. User 7 belongs only to it.
                sql(f"INSERT INTO groups(group_id,name,created_by) VALUES ('{other_group}','Other','{ids[6]}');")
                sql(''.join(f"INSERT INTO group_members(group_id,user_id) VALUES ('{other_group}','{u}');" for u in (ids[1], ids[3], ids[6])))
                sql(f"INSERT INTO shares(group_id,user_id,sharing_enabled,share_live_location) VALUES ('{other_group}','{ids[1]}',true,true),('{other_group}','{ids[3]}',true,true);")
                sql(f"INSERT INTO locations(group_id,user_id,latitude,longitude,recorded_at) VALUES ('{other_group}','{ids[1]}',70,80,'2026-02-01T00:00:00Z'),('{other_group}','{ids[3]}',71,81,'2026-02-01T00:00:00Z');")

                def token(user, *, issuer='closecircle', expires=None, signing_secret=secret):
                    head = b64(json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode())
                    body = b64(json.dumps({'iss': issuer, 'sub': user, 'exp': expires if expires is not None else int(time.time()) + 3600}).encode())
                    message = f'{head}.{body}'
                    return message + '.' + b64(hmac.new(signing_secret.encode(), message.encode(), hashlib.sha256).digest())

                def request(user=None, suffix='', *, group_id=group, bearer=None):
                    credential = bearer if bearer is not None else (token(user) if user else None)
                    headers = {'Authorization': 'Bearer ' + credential} if credential else {}
                    req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/locations/latest{suffix}', headers=headers)
                    try:
                        with urllib.request.urlopen(req) as response:
                            assert 'no-store' in response.headers.get('Cache-Control', '')
                            return response.status, json.load(response)
                    except urllib.error.HTTPError as error:
                        body = error.read()
                        return error.code, json.loads(body) if body else None

                assert request()[0] == 401
                assert request(bearer='not-a-jwt')[0] == 401
                assert request(bearer=token(ids[0], signing_secret='wrong-synthetic-secret-at-least-32-bytes'))[0] == 401
                assert request(bearer=token(ids[0], issuer='wrong-issuer'))[0] == 401
                assert request(bearer=token(ids[0], expires=int(time.time()) - 600))[0] == 401
                assert request(bearer=token('not-a-uuid'))[0] == 401
                status, problem = request(ids[6])
                assert status == 404 and problem['code'] == 'GROUP_NOT_FOUND'
                status, absent = request(ids[0], group_id=missing_group)
                assert status == 404 and absent['code'] == problem['code']
                assert request(ids[0], group_id=other_group)[0] == 404
                status, other = request(ids[6], group_id=other_group)
                assert status == 200 and {item['userId'] for item in other['items']} == {ids[1], ids[3]}
                other_point = next(item for item in other['items'] if item['userId'] == ids[1])['location']
                assert other_point['groupId'] == other_group and other_point['latitude'] == 70
                status, first = request(ids[0], '?limit=1')
                assert status == 200 and len(first['items']) == 1 and first['nextCursor']
                status, second = request(ids[0], '?limit=1&cursor=' + first['nextCursor'])
                assert status == 200 and len(second['items']) == 1 and second['nextCursor'] is None
                items = first['items'] + second['items']
                assert {item['userId'] for item in items} == set(ids[1:3])
                point = next(item for item in items if item['userId'] == ids[1])['location']
                assert point['groupId'] == group and point['latitude'] == 3 and point['longitude'] == 4 and point['accuracyM'] == 6.5
                assert next(item for item in items if item['userId'] == ids[2])['location'] is None
                status, problem = request(ids[0], '?cursor=not-valid')
                assert status == 400 and problem['code'] == 'INVALID_CURSOR'
                sql(f"INSERT INTO locations(group_id,user_id,latitude,longitude,recorded_at) VALUES ('{group}','{ids[1]}',5,6,'2026-01-03T00:00:00Z');")
                status, updated = request(ids[0])
                assert status == 200 and next(item for item in updated['items'] if item['userId'] == ids[1])['location']['latitude'] == 5
                sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
                status, after = request(ids[0])
                assert status == 200 and {item['userId'] for item in after['items']} == {ids[2]}
                status, other_after = request(ids[6], group_id=other_group)
                assert status == 200 and {item['userId'] for item in other_after['items']} == {ids[1], ids[3]}
                sql(f"UPDATE shares SET share_live_location=false WHERE group_id='{group}' AND user_id='{ids[2]}';")
                assert request(ids[0])[1]['items'] == []
                sql(f"DELETE FROM group_members WHERE group_id='{group}' AND user_id='{ids[0]}';")
                assert request(ids[0])[0] == 404
                print('PASS: invalid JWTs, membership, group isolation, live sharing, latest point, pagination and revocation')
        finally:
            if app is not None:
                app.terminate()
                try:
                    app.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    app.kill()
                    app.wait()
            if server_started:
                subprocess.run(['pg_ctl', '-D', str(data), '-m', 'fast', '-w', 'stop'],
                               check=True, stdout=subprocess.DEVNULL, env=safe_env)


if __name__ == '__main__':
    main()
