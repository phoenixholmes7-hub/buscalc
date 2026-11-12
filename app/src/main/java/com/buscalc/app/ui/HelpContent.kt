package com.buscalc.app.ui

sealed interface HelpBlock {
    data class Para(val text: String) : HelpBlock

    /** A worked example: each step is the keys to press and what happens. */
    data class Steps(val steps: List<Pair<String, String>>) : HelpBlock
}

data class HelpTopic(val title: String, val blocks: List<HelpBlock>)

data class HelpSection(val title: String, val topics: List<HelpTopic>)

private fun topic(title: String, vararg blocks: HelpBlock) = HelpTopic(title, blocks.toList())
private fun p(text: String) = HelpBlock.Para(text)
private fun steps(vararg s: Pair<String, String>) = HelpBlock.Steps(s.toList())

/** In the examples, ▲ is the gold upper-shift key and ▼ is the aqua lower-shift key. */
val HelpSections: List<HelpSection> = listOf(
    HelpSection(
        "Start here",
        listOf(
            topic(
                "How the keys work",
                p("Each key has up to three jobs. The large label is what the key does on its own."),
                p("The gold label above a key is reached by pressing the gold ▲ key first. The aqua label on the bottom edge of a key is reached by pressing the aqua ▼ key first. Press a shift key a second time to cancel it."),
                p("The strip between the display and the keys shows the values currently stored. Tap it to step through the groups: loan values, bond, break-even, margin, cash flows and statistics."),
            ),
            topic(
                "Storing a value or solving for it",
                p("N, I/YR, PV, PMT and FV, and the margin, break-even and bond keys, each hold a value. Type a number and press the key to store it. Press the key without typing a number first, straight after another of these keys, and it solves for that value from the others."),
                p("To see what a key is holding without solving, press RCL and then the key."),
                steps(
                    "360 N" to "stores 360 payments",
                    "PMT" to "solves for the payment",
                    "RCL PV" to "shows the stored loan amount",
                ),
            ),
            topic(
                "Why is my answer wrong?",
                p("Almost every surprising loan answer comes from one of four things."),
                p("Payments per year. The calculator starts at 12 payments a year. For a yearly problem, press 1 ▼ P/YR. The current setting is always shown under the number."),
                p("Signs. Money you receive is positive and money you pay out is negative. A loan you receive is a positive PV, so the payment comes back negative. If you enter PV and PMT with the same sign and solve for the rate, there is no answer."),
                p("Begin or End. Loans are normally paid at the end of each period, leases and savings plans often at the beginning. ▼ Beg/End switches, and the mode is shown under the number."),
                p("Leftover values. A value stays stored until you replace it. Store 0 in the ones you are not using, or press ▼ C ALL to start clean."),
            ),
            topic(
                "Order of operations",
                p("Sums follow the usual rules: multiplication and division are done before addition and subtraction, so 2 + 3 × 4 is 14. Use the brackets on ▼ RM and ▼ M+ to group things differently."),
                p("If you prefer every key to act on the running total, strictly left to right, switch with ▲ Alg/Chain. In chain mode 2 + 3 × 4 is 20."),
            ),
        ),
    ),
    HelpSection(
        "Loans and savings",
        listOf(
            topic(
                "Find a loan payment",
                p("A 30-year loan of 135,000 at 4.75% a year, paid monthly."),
                steps(
                    "12 ▼ P/YR" to "twelve payments a year",
                    "30 ▼ ×P/YR" to "360.00, stored in N",
                    "4.75 I/YR" to "yearly interest rate",
                    "135000 PV" to "amount borrowed",
                    "0 FV" to "nothing owed at the end",
                    "PMT" to "−704.22",
                ),
                p("Any one of the five values can be found the same way once the other four are stored. To see how much you could borrow with a 900 payment, key 900 +/− PMT and then press PV."),
            ),
            topic(
                "The five loan keys",
                p("N is the total number of payments. I/YR is the yearly interest rate as a percentage. PV is the value today: the loan amount, or an opening deposit. PMT is the regular payment. FV is the value at the end: a balloon payment, or a savings target."),
                p("×P/YR multiplies the number in the display by the payments per year and stores it in N, which turns years into payments."),
            ),
            topic(
                "Amortization schedule",
                p("With a loan stored, ▼ AMORT opens the schedule: principal, interest and remaining balance for every payment, with totals. Change the range on that screen and press Update, and share it from the icon at the top."),
                p("To open it on particular payments, give the range first."),
                steps("13 INPUT 24 ▼ AMORT" to "second year of a monthly loan"),
                p("The payment and each interest amount are rounded to the decimal places you are displaying, as a lender's statement is, so the columns add up exactly."),
            ),
            topic(
                "Nominal and effective rates",
                p("A nominal rate is the quoted yearly rate. The effective rate is what you actually earn or pay once compounding is included."),
                steps(
                    "5.25 ▼ NOM%" to "quoted rate",
                    "12 ▼ P/YR" to "compounded monthly",
                    "▼ EFF%" to "5.38",
                ),
                p("To go the other way, store the effective rate with ▼ EFF% and then press ▼ NOM%."),
            ),
            topic(
                "Savings and investments",
                p("Deposits are money paid out, so enter them as negatives. Saving 100 at the start of every month for three years at 10%:"),
                steps(
                    "▼ Beg/End" to "switch to BEGIN",
                    "36 N" to "",
                    "10 I/YR" to "",
                    "0 PV" to "",
                    "100 +/− PMT" to "",
                    "FV" to "4,213.00",
                ),
            ),
        ),
    ),
    HelpSection(
        "Business",
        listOf(
            topic(
                "Percentages",
                p("After + or −, the % key takes that percentage of the first number. After × or ÷ it simply divides by 100."),
                steps(
                    "200 + 15 % =" to "230.00",
                    "200 × 15 % =" to "30.00",
                    "80 INPUT 100 ▼ %CHG" to "25.00, the change from 80 to 100",
                ),
            ),
            topic(
                "Cost, price, margin and markup",
                p("Margin is profit as a percentage of the selling price. Markup is profit as a percentage of cost. Store any two of cost, price and one percentage, then press the key you want."),
                steps(
                    "9.60 CST" to "cost",
                    "15 PRC" to "selling price",
                    "MAR" to "36.00",
                    "MU" to "56.25",
                ),
                steps(
                    "20 CST" to "",
                    "25 MAR" to "want a 25% margin",
                    "PRC" to "26.67",
                ),
            ),
            topic(
                "Break-even",
                p("Five values on the gold layer: UNITS sold, SP selling price each, VC variable cost each, FC fixed costs, and PROFIT. Store four and press the fifth."),
                steps(
                    "300 ▲ SP" to "",
                    "250 ▲ VC" to "",
                    "150000 ▲ FC" to "",
                    "10000 ▲ PROFIT" to "target profit",
                    "▲ UNITS" to "3,200.00",
                ),
                p("Store 0 in PROFIT to find the plain break-even point."),
            ),
            topic(
                "Depreciation",
                p("Depreciation borrows the loan keys: N is the useful life in years, PV the cost, FV the salvage value, and I/YR the declining-balance factor (200 for double declining). Then key the year and press SL, SOYD or DB."),
                steps(
                    "5 N" to "five-year life",
                    "10000 PV" to "cost",
                    "500 FV" to "salvage value",
                    "200 I/YR" to "double declining",
                    "1 ▲ SL" to "1,900.00 straight line",
                    "▼ SWAP" to "7,600.00 still to depreciate",
                    "2 ▲ DB" to "2,400.00 in year 2",
                ),
            ),
            topic(
                "Constants",
                p("K remembers an operation so you can repeat it on new numbers."),
                steps(
                    "5 + 2 K =" to "7.00",
                    "6 =" to "8.00",
                    "25 =" to "27.00",
                ),
            ),
        ),
    ),
    HelpSection(
        "Investments",
        listOf(
            topic(
                "Cash flows: NPV and IRR",
                p("Enter the flows in order with CFj, starting with the outlay today. Money out is negative. Then ▼ NPV gives the net present value at the rate in I/YR, and ▼ IRR/YR gives the internal rate of return."),
                steps(
                    "1 ▼ P/YR" to "yearly flows",
                    "80000 +/− CFj" to "pay 80,000 today",
                    "500 +/− CFj" to "year 1",
                    "4500 CFj" to "year 2",
                    "5500 CFj" to "year 3",
                    "4500 CFj" to "year 4",
                    "130000 CFj" to "year 5, sale",
                    "13 I/YR ▼ NPV" to "212.18",
                    "▼ IRR/YR" to "13.06",
                ),
                p("A flow that repeats can be entered once: 1000 INPUT 5 CFj, or 1000 CFj followed by 5 ▼ Nj. The Cash flows screen in the menu shows the whole list and lets you edit it."),
            ),
            topic(
                "Bonds",
                p("Bond values are on the gold layer of the top two rows. Dates are typed as numbers: 4.282004 is April 28, 2004. Prices are per 100 of face value."),
                steps(
                    "4.282004 ▲ SetDate" to "settlement date",
                    "6.042018 ▲ MatDate" to "maturity date",
                    "6.75 ▲ CPN%" to "coupon rate",
                    "4.75 ▲ YTM" to "yield wanted",
                    "▲ PRICE" to "120.38",
                    "▲ AccInt" to "2.69 accrued interest",
                ),
                p("To find the yield instead, store the price and press ▲ YTM. CALL is the redemption value and starts at 100. ▲ Semi/Ann switches between two coupons a year and one, and ▲ 360/Act switches the day count."),
            ),
            topic(
                "Dates",
                p("Type dates as MM.DDYYYY. ▲ DMY/MDY switches to DD.MMYYYY."),
                steps(
                    "4.282010 INPUT 100 ▼ DATE" to "08-06-2010, a Friday",
                    "4.282010 INPUT 6.042020 ▼ ΔDAYS" to "3,690.00 days",
                    "▼ SWAP" to "3,636.00 on a 360-day year",
                ),
            ),
        ),
    ),
    HelpSection(
        "Statistics and math",
        listOf(
            topic(
                "Statistics and regression",
                p("Add each value with Σ+. For pairs, key x, INPUT, y, then Σ+. Σ− removes an entry and ▼ C STAT starts over."),
                steps(
                    "1 INPUT 2 Σ+" to "1.00, one pair entered",
                    "2 INPUT 4 Σ+" to "2.00",
                    "3 INPUT 5 Σ+" to "3.00",
                    "▼ x̄,ȳ" to "average of x",
                    "▼ SWAP" to "average of y",
                    "4 ▼ ŷ,m" to "predicted y when x is 4",
                    "▼ SWAP" to "slope of the line",
                ),
                p("Keys that give two answers show the first, and ▼ SWAP shows the second: Sx,Sy sample deviations; σx,σy population deviations; x̂,r estimate and correlation; x̄w,b weighted mean and intercept. The sums are on the gold layer of the number keys."),
            ),
            topic(
                "Math functions",
                p("The aqua layer of the right-hand column and bottom rows holds 1/x, y^x, √x, x², e^x, LN, n! and π. The gold layer holds SIN, COS, TAN, and nPr, nCr and RAND."),
                steps(
                    "2 ▼ y^x 10 =" to "1,024.00",
                    "30 ▲ SIN" to "0.50",
                    ".5 ▲ INV ▲ SIN" to "30.00",
                    "52 INPUT 5 ▲ nCr" to "2,598,960.00",
                ),
                p("▲ HYP before a trig key gives the hyperbolic version. ▲ Rad/Deg switches angle units."),
            ),
            topic(
                "Memory",
                p("→M stores the display, RM brings it back and M+ adds to it. There are twenty numbered registers as well."),
                steps(
                    "42 ▼ STO 3" to "store in register 3",
                    "RCL 3" to "42.00",
                    "7 ▼ STO . 2" to "store in register 12",
                ),
                p("Registers & memory in the menu lists everything that is stored."),
            ),
        ),
    ),
    HelpSection(
        "Display and clearing",
        listOf(
            topic(
                "Decimal places and number format",
                steps(
                    "▼ DISP 4" to "four decimal places",
                    "▼ DISP ." to "floating: all digits",
                    "▼ DISP =" to "peek at all digits once",
                    "▼ ,/." to "swap comma and point",
                    "▼ RND" to "round the value to what is shown",
                ),
                p("Changing the decimal places only changes what is shown. The full value is kept for the next step unless you round it."),
            ),
            topic(
                "Clearing",
                p("The backspace key removes the last digit typed. C clears the number and the sum in progress. ▼ C ALL clears every stored value but keeps your settings and the list of recent calculations."),
                p("Use the Clear buttons on the Registers & memory screen to clear just one group."),
            ),
            topic(
                "Recent calculations",
                p("Every answer is added to Recent calculations in the menu, with the values that produced it. Tap an entry to put its result back in the display. Copy value and Paste value in the menu move numbers to and from other apps."),
            ),
        ),
    ),
)
