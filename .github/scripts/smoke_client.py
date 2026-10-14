"""Launch the Fabric client under Xvfb and verify startup plus mixin audit."""
import os
from pathlib import Path
import signal
import subprocess
import time

log = Path('build/client-smoke.log')
log.parent.mkdir(exist_ok=True)
with log.open('w') as output:
    process = subprocess.Popen(
        ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24 +extension GLX +render -noreset', './gradlew', 'runClient', '--no-daemon'],
        stdout=output, stderr=subprocess.STDOUT, start_new_session=True,
    )
    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            text = log.read_text(errors='replace')
            if 'Bedrock Miner mixin audit passed' in text:
                print('Minecraft 26.3 client started and mixin audit passed.')
                break
            if process.poll() is not None:
                raise RuntimeError(f'Client exited before startup (exit {process.returncode}).')
            time.sleep(1)
        else:
            raise RuntimeError('Client startup/mixin audit timed out.')
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
        print(log.read_text(errors='replace')[-14000:])
