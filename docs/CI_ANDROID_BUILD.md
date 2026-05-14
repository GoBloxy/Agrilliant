# Cloud Android APK Build — GitHub Actions

This repo ships a GitHub Actions workflow that builds the Android APK on a
Linux runner in the cloud, so Windows-host developers can produce APKs
without WSL2 or a local Linux toolchain.

The workflow file lives at `.github/workflows/android-build.yml`.

---

## One-time setup

### 1. Push the branch to GitHub

This workflow file must be on the branch you're building. Push `mobile-app`
(or whatever branch your code lives on) to GitHub.

### 2. Add the GitHub Secrets

Go to your repo on github.com → **Settings** → **Secrets and variables** →
**Actions** → **New repository secret**. Add each of these:

| Secret name | Required | Example value |
|-------------|:---:|---|
| `DB_URL` | ✅ | `jdbc:mysql://139.59.153.80:3306/agrilliant?useSSL=false&serverTimezone=UTC` |
| `DB_USER` | ✅ | `agrilliant_user` |
| `DB_PASSWORD` | ✅ | `<your MySQL password>` |
| `MQTT_BROKER` | ✅ | `ssl://abcd1234.s1.eu.hivemq.cloud:8883` |
| `MQTT_USERNAME` | optional | `<HiveMQ Cloud username>` (empty for open brokers) |
| `MQTT_PASSWORD` | optional | `<HiveMQ Cloud password>` (empty for open brokers) |
| `CROP_HEALTH_API_KEY` | optional | `<your Kindwise API key>` (build still succeeds without it; disease-detection feature disabled in the APK) |
| `MQTT_TOPIC_PREFIX` | optional | defaults to `agrilliant/sensors` |
| `MQTT_CLIENT_ID_PREFIX` | optional | defaults to `agrilliant-` |
| `MQTT_QOS` | optional | defaults to `1` |

These secrets are never written to the repo or visible in logs — the workflow
uses them to generate the `.properties` files at build time, then the runner
is destroyed.

---

## Trigger a build

Two ways:

### Manual

1. Go to your repo on github.com → **Actions** tab.
2. Click **"Android APK Build"** in the left sidebar.
3. Click the **"Run workflow"** dropdown on the right → pick a branch
   (usually `mobile-app`) → click **"Run workflow"**.
4. Wait ~20–40 min for the first run (subsequent runs are faster due to
   Maven + GraalVM caching).

### Automatic on push

Any push to `mobile-app` that touches `src/**`, `pom.xml`, or the workflow
file itself triggers the build automatically.

---

## Download the APK

1. Once the workflow run completes, click into it from the Actions tab.
2. Scroll to the bottom — **Artifacts** section.
3. Download **`agrilliant-apk`** (a zip containing the .apk file).
4. Unzip to get `<binary>.apk`.

The artifact is retained for 14 days.

---

## Install on a device

From your Windows machine (you need `adb` on PATH — get it via Android
Studio's SDK Manager or by extracting platform-tools.zip from
https://developer.android.com/tools/releases/platform-tools):

```powershell
adb devices                    # confirm your phone shows up (USB debugging must be on)
adb install path\to\Agrilliant.apk
adb logcat -s GraalVM:V GluonAttach:V Agrilliant:V
# Launch from the device's app drawer
```

Before launching, also apply the SQL migration on your MySQL:

```powershell
# From any machine that can reach the DB:
mysql -h 139.59.153.80 -u <user> -p agrilliant < docs/sql/2026-05-14-fix-growthstage-growing.sql
```

---

## Troubleshooting

| Symptom in workflow run | Likely cause | Fix |
|--------------------------|--------------|-----|
| Step "Set up GraalVM" fails to download | GitHub Actions rate limit | Re-run; usually transient. Check Actions tab for runner status. |
| Step "GluonFX build (AOT native-image)" runs > 60 min then times out | Runner OOM (the default 7 GB isn't enough for some builds) | Edit `.github/workflows/android-build.yml`, change `runs-on: ubuntu-22.04` to `runs-on: ubuntu-22.04-l` (large, 16 GB — billed) |
| AOT halts on `ClassNotFoundException` | `reflect-config.json` missing an entry for a class that FXMLLoader instantiates | Run on desktop locally with tracing agent (`mvn -Pdesktop -Dgluonfx.run.with.agent=true gluonfx:nativerun`), exercise the failing screen, merge `target/agent-output/.../reflect-config.json` into `src/main/resources/META-INF/native-image/smartfarm/reflect-config.json` |
| AOT halts on `mysql-connector-j` reflection | Same root cause as above for the MySQL driver | Same agent-tracing fix |
| `gluonfx:package` step says "No APK found" | `gluonfx:build` actually failed but exit code 0 was masked | Scroll up in the log to the `gluonfx:build` step; the real error is there |
| Workflow doesn't trigger on push | The push didn't touch any path in the workflow's `paths` filter | Either push a `src/` or `pom.xml` change, or use manual `workflow_dispatch` |

---

## Cost

- **GitHub Free / Pro accounts:** 2,000 free Actions minutes/month for private repos; **unlimited for public repos**. A typical Android build run is ~25 min, so a free account handles ~80 builds/month.
- **Larger runners** (`ubuntu-22.04-l`, 16 GB RAM): ~$0.024/min if needed for OOM cases. Same build at ~15 min wall-clock ≈ $0.36/build.

---

## Why this exists

GluonFX's Android cross-compile toolchain (GraalVM native-image + Substrate
+ Android NDK) does not run natively on Windows. Phase 1 of this project
was developed on Windows; this workflow is the team's chosen path to
producing APKs without forcing each developer to maintain a WSL2 + Ubuntu
environment locally.

If you do want a local Linux build, see `scripts/wsl-setup-android-build-env.sh`
for the automated WSL2 setup path.
