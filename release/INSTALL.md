# Installing the released APK

The official **GitHub Release** for this project is created by the CI workflow
(`ci/release.yml`, installed to `.github/workflows/release.yml` by
`scripts/enable_release.sh`). The Release page carries the APK as a proper
release asset:

    https://github.com/YOUSSEFJEDIDI89/Maui/releases

If you cannot reach the Release page, the exact APK is also mirrored here as
base64 chunks, so it can always be reconstructed byte-for-byte:

```bash
# from a clone of this repository
cd release
cat Maui-v1.0-release.apk.b64.* | base64 -d > Maui-v1.0-release.apk
sha256sum Maui-v1.0-release.apk
# expected: 8c2adb5794aa885b235680bba94de2e18c077c1d67f82666e93e55f3bac518b0
```

Then install it on the phone (allow "install from unknown sources") and open **Maui**.
