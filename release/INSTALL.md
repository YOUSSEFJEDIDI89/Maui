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
# expected: 3089ef3794141768b4a4d262c32036c4c62c2f7df7c1849196c696aa18e3b216
```

Then install it on the phone (allow "install from unknown sources") and open **Maui**.
