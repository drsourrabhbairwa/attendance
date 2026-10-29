# Security policy

This repository is the Amitek staff-attendance app: the web page (`index.html` and its helper scripts, served by GitHub Pages) and the Android app that loads it.
The server side runs on Firebase and is not part of this repository.

## Reporting a problem

Please do **not** open a public issue for a security problem.

Use GitHub's private reporting instead: open the **Security** tab of this repository and choose **Report a vulnerability**.
Describe what you found, how to reproduce it and what it could expose. You will get an answer within a few days.

## What is worth reporting

- A way to see or change another person's attendance, location, photo or messages
- A way to sign in without the right password, or to keep a sign-in alive after it should have ended
- Any password, token or key that the page or the app stores or sends where it should not
- A way to make the page or the app run someone else's script

## What the app already does

- On the live setup, signing in gives the phone a private token; the phone does not keep the password or its hash.
- The Android print view runs with JavaScript and file access switched off.
- The Android build tools and the workflow actions are checked weekly by Dependabot.
