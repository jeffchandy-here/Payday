# Payday

Simple salary-cycle money tracker. Each month runs from the 28th (salary day) to the 27th,
and is named after the month the salary is for (Sep 28 – Oct 27 = "October").

## Build the APK on GitHub
1. Create a **private** repo on GitHub.
2. Upload everything in this folder, including the hidden `.github` folder and `app/payday.jks`.
3. Go to the **Actions** tab. "Build Payday APK" runs on every push to `main`
   (or click **Run workflow**).
4. When it finishes (about 4–6 min), open the run and download **Payday-apk** under Artifacts.
   Unzip it and install `Payday.apk` on your phone.

## Updating
Every build is signed with the same key (`app/payday.jks`), so a new APK installs over the old one
and your data stays. Bump `versionCode` in `app/build.gradle.kts` for each new release.
Keep the repo private, since the signing key is in it.

## How it works
- **Salary**: ₹1,00,000 by default. Edit it in Monthly, and it applies from this month onward.
- **Monthly costs** (rent, EMI, SIP…): counted every cycle until you delete them.
  Editing or deleting applies from the current month, past months stay as they were.
- **Expenses**: one-off spends, counted in the cycle their date falls in.
- Data is stored on the phone in the app's private storage. Uninstalling the app deletes it.
