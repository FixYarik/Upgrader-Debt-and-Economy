#!/usr/bin/env bash
set -euo pipefail
setup=/workspace/.setup
mkdir -p "$setup/downloads" "$setup/tools"
fetch() {
  local url="$1" target="$2"
  if [ ! -s "$target" ]; then
    curl -fsSL --retry 2 --connect-timeout 20 --max-time 180 "$url" -o "$target.part"
    mv "$target.part" "$target"
  fi
}
fetch 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz' "$setup/downloads/jdk17.tar.gz"
fetch 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.17%2B10/OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz.sha256.txt' "$setup/downloads/jdk17.sha256"
fetch 'https://services.gradle.org/distributions/gradle-8.8-bin.zip' "$setup/downloads/gradle.zip"
fetch 'https://services.gradle.org/distributions/gradle-8.8-bin.zip.sha256' "$setup/downloads/gradle.sha256"
python - <<'PY'
from pathlib import Path
import hashlib,tarfile,zipfile
root=Path('/workspace/.setup')
for archive,checksum in [('jdk17.tar.gz','jdk17.sha256'),('gradle.zip','gradle.sha256')]:
    p=root/'downloads'/archive
    assert hashlib.sha256(p.read_bytes()).hexdigest()==(root/'downloads'/checksum).read_text().split()[0], 'SHA-256 mismatch: '+archive
if not (root/'tools/jdk-17.0.17+10/bin/javac').exists():
    with tarfile.open(root/'downloads/jdk17.tar.gz') as t: t.extractall(root/'tools',filter='data')
if not (root/'tools/gradle-8.8/bin/gradle').exists():
    with zipfile.ZipFile(root/'downloads/gradle.zip') as z: z.extractall(root/'tools')
print('JDK and Gradle SHA-256 verified')
PY
cp /etc/ssl/certs/java/cacerts "$setup/truststore"
"$setup/tools/jdk-17.0.17+10/bin/keytool" -importcert -noprompt -alias environment-proxy -file /usr/local/share/ca-certificates/environment-proxy-ca.crt -keystore "$setup/truststore" -storepass changeit
cat > "$setup/env.sh" <<'ENV'
export JAVA_HOME=/workspace/.setup/tools/jdk-17.0.17+10
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.setup/gradle-cache
ude_gradle() {
  bash /workspace/.setup/tools/gradle-8.8/bin/gradle --no-daemon --max-workers=2 -Djavax.net.ssl.trustStore=/workspace/.setup/truststore -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 "$@"
}
ENV
source "$setup/env.sh"
cd /workspace/Upgrader-Debt-and-Economy
python scripts/test_core.py
ude_gradle --version

# Headless client support, installed locally using signed Debian metadata.
if [ ! -x "$setup/display/usr/bin/Xvfb" ]; then
  mkdir -p "$setup/apt/lists/partial" "$setup/apt/cache/archives/partial" "$setup/apt/debs"
  printf '%s\n' 'deb [signed-by=/usr/share/keyrings/debian-archive-keyring.gpg] https://deb.debian.org/debian trixie main' > "$setup/apt/sources.list"
  apt_args=(-o Dir::Etc::parts=- -o Dir::Etc::sourcelist="$setup/apt/sources.list" -o Dir::Etc::sourceparts=- -o Dir::State::lists="$setup/apt/lists" -o Dir::Cache="$setup/apt/cache")
  apt-get "${apt_args[@]}" update
  (cd "$setup/apt/debs" && apt-get "${apt_args[@]}" download xvfb)
  for package in "$setup"/apt/debs/xvfb_*.deb; do dpkg-deb -x "$package" "$setup/display"; done
fi
# Official dependency is retained for local testing only; never bundle it in UDE.
fetch 'https://cdn.modrinth.com/data/UQgc9Wcb/versions/nIG81STn/upgrader-forge-1.2.1%2B1.20.1.jar' "$setup/downloads/upgrader-items.jar"
python - <<'PY'
from pathlib import Path
import hashlib,shutil
root=Path('/workspace/.setup')
jar=root/'downloads/upgrader-items.jar'
assert hashlib.sha512(jar.read_bytes()).hexdigest()=='849449e21ae6ed10a367b9f7179b72660be22d4db5dd5d3557660f6f0104a563ed32cc25979060674b37cc12c8b4eff45d1dbe09a327f0a78c0502adc85be9a0'
p=root/'maven/local/upgrader/upgrader/1.2.1'
p.mkdir(parents=True,exist_ok=True)
shutil.copyfile(jar,p/'upgrader-1.2.1.jar')
(p/'upgrader-1.2.1.pom').write_text('<project><modelVersion>4.0.0</modelVersion><groupId>local.upgrader</groupId><artifactId>upgrader</artifactId><version>1.2.1</version></project>')
(root/'compat.init.gradle').write_text("""allprojects {
    afterEvaluate {
        repositories { maven { url = uri('/workspace/.setup/maven') } }
        dependencies { runtimeOnly fg.deobf('local.upgrader:upgrader:1.2.1') }
        tasks.withType(JavaExec).configureEach {
            if (name == 'runServer') standardInput = System.in
            systemProperty 'https.proxyHost', 'proxy'
            systemProperty 'https.proxyPort', '8080'
            systemProperty 'http.proxyHost', 'proxy'
            systemProperty 'http.proxyPort', '8080'
            systemProperty 'javax.net.ssl.trustStore', '/workspace/.setup/truststore'
        }
    }
}
""")
print('Upgrader Items SHA-512 verified; dev remapping configured')
PY
# Both meaningful executable test suites are dependencies of check/build.
cd /workspace/Upgrader-Debt-and-Economy
ude_gradle build
