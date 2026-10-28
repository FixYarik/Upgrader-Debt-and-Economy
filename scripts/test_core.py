#!/usr/bin/env python3
"""Offline Java 17-targeted compilation and deterministic economy tests."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
out=root/'build'/'core-test'
out.mkdir(parents=True,exist_ok=True)
files=sorted((root/'src/main/java/dev/ude/core').glob('*.java'))+sorted((root/'src/test/java/dev/ude/core').glob('*.java'))
subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(out),*[str(f) for f in files]],check=True)
subprocess.run(['java','-cp',str(out),'dev.ude.core.EconomyEngineTest'],check=True)
