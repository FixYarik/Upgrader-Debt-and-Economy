# Облачная среда разработки

Используйте существующий checkout `/workspace/Upgrader-Debt-and-Economy`. Среда уже изолирована; не создавайте worktree без отдельного указания.

Подготовка в управляемой Debian-среде Codex:

```bash
bash scripts/cloud/setup.sh
source /workspace/.setup/env.sh
ude_gradle build
```

Скрипт предназначен для облачной среды с настроенными proxy и CA. Он устанавливает JDK 17 и Gradle 8.8 локально, проверяет SHA-256, подготавливает Xvfb и проверенный по SHA-512 Upgrader Items для dev-запуска. Подписи Debian и TLS остаются включены. Сторонний мод не включён в UDE.

Сервер с преобразованием имён зависимости через ForgeGradle:

```bash
ude_gradle -I /workspace/.setup/compat.init.gradle runServer -x downloadAssets --args='--nogui'
```

Для нового сервера необходимо самостоятельно принять Minecraft EULA. Тестовый сервер в текущей среде использует отдельный мир `ude-smoke`, `server-ip=127.0.0.1` и `online-mode=false`. Это конфигурация локального теста, не публичного сервера. Консоль поддерживает `ude help` и штатную остановку `stop`.

Для клиента запустите Xvfb, затем в другом терминале:

```bash
/workspace/.setup/display/usr/bin/Xvfb :99 -screen 0 1280x720x24 -nolisten tcp
# В другом терминале:
source /workspace/.setup/env.sh
cd /workspace/Upgrader-Debt-and-Economy
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null ude_gradle -I /workspace/.setup/compat.init.gradle runClient
```

Не кладите production-JAR Upgrader Items непосредственно в `run/mods`: для dev-запуска нужен `fg.deobf`, уже настроенный init script. Процессы после восстановления snapshot требуется запустить заново.

Точные результаты и ограничения приведены в [VALIDATION_REPORT.md](VALIDATION_REPORT.md). В `dist/` сохранены alpha-JAR, архив исходников и SHA-256. Это частичный прототип, не законченный релиз.
