<table>
  <tr>
    <td width="140" valign="top">
      <img src="media/logo.png" alt="SelfEra logo" width="120"/>
    </td>
    <td valign="top">
      <h1>SelfEra</h1>
      <p><em>App for color analysis exploration and booking beauty services. An instrument for beauty masters to handle their business.</em></p>
    </td>
  </tr>
</table>

---

## Architecture

SelfEra follows a **client–server** model. The Android client is built with **Kotlin**, **Jetpack Compose**, and **Retrofit** for a responsive mobile experience. The REST API is powered by **Django** and **PostgreSQL**, with **Cloudinary** handling profile photos and outfit assets.

The backend lives in a separate repository: **[beautyAppBackend](https://github.com/sana23ok/beautyAppBackend)**. The production API is hosted at `https://beautyappbackend.onrender.com/`.

---

## Deployment

| Component | Platform |
|-----------|----------|
| REST API | [Render](https://render.com) |
| Database | Render PostgreSQL |
| Media storage | Cloudinary |

The backend deploys via Render with automatic migrations and environment-based configuration. The mobile app reads the API base URL from `local.properties` at build time.

---

## Installation

Download and install the latest Android build on your device:

📥 [**Download SelfEra APK**](media/SelfEra.apk)

> To build the APK locally, set `api.base.url=https://beautyappbackend.onrender.com/` in `local.properties`, then run:
> ```bash
> ./gradlew :app:assembleDebug
> ```
> Copy `app/build/outputs/apk/debug/app-debug.apk` to `media/SelfEra.apk`.

**Requirements:** Android 7.0 (API 24) or higher.

---

## Features

- **Color & appearance analysis** — personalized recommendations by seasonal color type, body shape, and style goals
- **Master search & booking** — browse beauty professionals, view services, and schedule appointments
- **Master workspace** — manage price lists, availability, bookings, and client communication
- **In-app chat** — real-time messaging between clients and masters
- **Outfit inspiration** — curated looks matched to your color palette via Cloudinary
- **Google Sign-In** — quick and secure authentication
- **Moderation tools** — admin review flow for master profiles

---

## Video demonstration of an app

Watch a walkthrough of the sign-in flow, navigation, and core features:

<p align="center">
  <a href="https://youtu.be/fy1KcxZ2toU">
    <img src="media/video_thumbnail.png" alt="SelfEra app demo — click to watch on YouTube" width="720"/>
  </a>
</p>

<p align="center">
  ▶️ <a href="https://youtu.be/fy1KcxZ2toU"><strong>Watch on YouTube</strong></a>
</p>

---

<div align="center">

**SelfEra** — discover your colors, connect with masters, book with confidence.

Built with Kotlin · Django · PostgreSQL · Cloudinary

</div>
