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
    backend = Path(__file__).resolve().parents[3]
    jar = backend / 'target' / 'closecircle-api.jar'
    if not jar.exists():
        raise SystemExit('Build the backend jar first; see the module docstring.')
    pg_port, api_port = free_port(), free_port()
    secret = 'synthetic-local-test-secret-32-bytes-only'
    with tempfile.TemporaryDirectory(prefix='cc-location-test-') as temp:
        data = Path(temp) / 'pg'
        server_started = False
        app = None
        try:
            subprocess.run(['initdb', '-D', str(data), '-A', 'trust', '--no-locale'],
                           check=True, stdout=subprocess.DEVNULL)
            subprocess.run(['pg_ctl', '-D', str(data), '-l', str(Path(temp) / 'pg.log'),
                            '-o', f'-p {pg_port} -h 127.0.0.1 -k {temp}', '-w', 'start'],
                           check=True, stdout=subprocess.DEVNULL)
            server_started = True
            subprocess.run(['createdb', '-h', '127.0.0.1', '-p', str(pg_port),
                            'closecircle_test_scrum19'], check=True)
            env = dict(os.environ, DATABASE_URL=f'jdbc:postgresql://127.0.0.1:{pg_port}/closecircle_test_scrum19?sslmode=disable',
                       DATABASE_USERNAME=getpass.getuser(), DATABASE_PASSWORD='', JWT_SECRET=secret,
                       PORT=str(api_port))
            # Ignore unrelated database settings from the caller's shell.
            for name in ('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
                         'DATABASE_URL_UNPOOLED', 'FLYWAY_DATABASE_URL', 'FLYWAY_DATABASE_USERNAME', 'FLYWAY_DATABASE_PASSWORD'):
                env.pop(name, None)
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

                db = ['psql', '-h', '127.0.0.1', '-p', str(pg_port), '-d', 'closecircle_test_scrum19',
                      '-v', 'ON_ERROR_STOP=1', '-q']
                def sql(text):
                    subprocess.run(db, input=text, text=True, check=True, stdout=subprocess.DEVNULL)

                group = '10000000-0000-0000-0000-000000000001'
                ids = [f'00000000-0000-0000-0000-{n:012d}' for n in range(1, 8)]
                sql(''.join(f"INSERT INTO users(user_id,email,password_hash,display_name) VALUES ('{u}','test{n}@example.invalid','x','User {n}');" for n, u in enumerate(ids, 1)))
                sql(f"INSERT INTO groups(group_id,name,created_by) VALUES ('{group}','Test','{ids[0]}');")
                sql(''.join(f"INSERT INTO group_members(group_id,user_id,joined_at) VALUES ('{group}','{u}','2026-01-01T00:00:00Z');" for u in ids[:6]))
                sql(f"INSERT INTO shares(group_id,user_id,sharing_enabled,share_live_location) VALUES ('{group}','{ids[1]}',true,true),('{group}','{ids[2]}',true,true),('{group}','{ids[3]}',false,true),('{group}','{ids[4]}',true,false);")
                sql(f"INSERT INTO locations(group_id,user_id,latitude,longitude,accuracy_m,recorded_at) VALUES ('{group}','{ids[1]}',1,2,NULL,'2026-01-01T00:00:00Z'),('{group}','{ids[1]}',3,4,6.5,'2026-01-02T00:00:00Z'),('{group}','{ids[3]}',9,9,NULL,'2026-01-02T00:00:00Z');")

                def token(user):
                    head = b64(json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode())
                    body = b64(json.dumps({'iss': 'closecircle', 'sub': user, 'exp': int(time.time()) + 3600}).encode())
                    message = f'{head}.{body}'
                    return message + '.' + b64(hmac.new(secret.encode(), message.encode(), hashlib.sha256).digest())

                def request(user=None, suffix=''):
                    headers = {'Authorization': 'Bearer ' + token(user)} if user else {}
                    req = urllib.request.Request(f'{base}/api/v1/groups/{group}/locations/latest{suffix}', headers=headers)
                    try:
                        with urllib.request.urlopen(req) as response:
                            assert 'no-store' in response.headers.get('Cache-Control', '')
                            return response.status, json.load(response)
                    except urllib.error.HTTPError as error:
                        body = error.read()
                        return error.code, json.loads(body) if body else None

                assert request()[0] == 401
                status, problem = request(ids[6])
                assert status == 404 and problem['code'] == 'GROUP_NOT_FOUND'
                status, first = request(ids[0], '?limit=1')
                assert status == 200 and len(first['items']) == 1 and first['nextCursor']
                status, second = request(ids[0], '?limit=1&cursor=' + first['nextCursor'])
                assert status == 200 and len(second['items']) == 1 and second['nextCursor'] is None
                items = first['items'] + second['items']
                assert {item['userId'] for item in items} == set(ids[1:3])
                point = next(item for item in items if item['userId'] == ids[1])['location']
                assert point['latitude'] == 3 and point['longitude'] == 4 and point['accuracyM'] == 6.5
                assert next(item for item in items if item['userId'] == ids[2])['location'] is None
                status, problem = request(ids[0], '?cursor=not-valid')
                assert status == 400 and problem['code'] == 'INVALID_CURSOR'
                sql(f"INSERT INTO locations(group_id,user_id,latitude,longitude,recorded_at) VALUES ('{group}','{ids[1]}',5,6,'2026-01-03T00:00:00Z');")
                status, updated = request(ids[0])
                assert status == 200 and next(item for item in updated['items'] if item['userId'] == ids[1])['location']['latitude'] == 5
                sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
                status, after = request(ids[0])
                assert status == 200 and {item['userId'] for item in after['items']} == {ids[2]}
                print('PASS: auth, membership, visibility, latest point and updates, accuracy, pagination, cursor and revocation')
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
                               check=True, stdout=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
