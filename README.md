# Holmes Business Calculator

A financial calculator for Android in the style of the classic 10bII business
calculators: a 40-key keypad with two shift layers, written in Kotlin and Jetpack Compose.

## Features

- **Time value of money**: N, I/YR, PV, PMT, FV, payments per year, begin/end mode
- **Amortization schedule** for any range of payments, with totals and sharing
- **Interest conversion** between nominal and effective rates
- **Cash flows**: NPV, IRR and NFV, with repeated flows and an editable list
- **Business**: percent, percent change, cost/price/margin/markup, break-even
- **Depreciation**: straight line, sum-of-years'-digits, declining balance
- **Bonds**: price, yield and accrued interest, annual or semiannual, actual or 30/360
- **Dates**: days between dates, date plus days
- **Statistics**: one- and two-variable sums, means, deviations, linear regression
- **Math**: powers, roots, logs, factorial, permutations, combinations, trig and hyperbolics
- Algebraic or chain entry, constants, parentheses, memory and 20 registers
- Recent-calculations tape, registers overview, built-in help, light and dark themes

## Install

A ready-to-install build is in [`dist/`](dist). Copy the APK to an Android phone
(Android 7.0 or later), open it, and allow installation from that source when asked.

## Build

Requires JDK 17 or later and the Android SDK (platform 37).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

```bash
./gradlew testDebugUnitTest
```

runs the tests: the calculation engine against published reference values, the
app driven end to end through its keypad, and a render of every screen to
`app/build/screenshots/`.

## Layout

- `app/src/main/java/com/buscalc/app/engine`: the calculator, with no Android dependencies
- `app/src/main/java/com/buscalc/app/ui`: the Compose screens
