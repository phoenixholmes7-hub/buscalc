package com.buscalc.app.engine

/** Physical keys, in keypad order (5 columns by 8 rows). */
enum class Key {
    N, IYR, PV, PMT, FV,
    INPUT, MU, CST, PRC, MAR,
    K, PCT, CFJ, SIGMA, BACK,
    CHS, RCL, TO_M, RM, M_PLUS,
    AQUA, D7, D8, D9, DIV,
    GOLD, D4, D5, D6, MUL,
    CLR, D1, D2, D3, SUB,
    HELP, D0, DOT, EQ, ADD,
}

/** Everything a key press can mean once the shift state is resolved. The ten digits come first so ordinal == digit. */
enum class Fn {
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
    DOT, CHS, EEX, BACK, CLR, CLR_ALL,
    ADD, SUB, MUL, DIV, POW, EQ, LPAREN, RPAREN,
    PCT, PCT_CHG, K, SWAP, INPUT, RND,
    INV_X, SQRT, SQUARE, EXP, LN, FACT, PI, RAND, NPR, NCR,
    SIN, COS, TAN, HYP, INV, RAD_DEG,
    STO, RCL, TO_M, RM, M_PLUS,
    N, IYR, PV, PMT, FV, XPYR, NOM, EFF, PYR, AMORT, BEG_END,
    MU, CST, PRC, MAR,
    CFJ, NJ, NPV, IRR,
    SIGMA_PLUS, SIGMA_MINUS, CLR_STAT, MEAN, SDEV, PSDEV, EST_X, EST_Y, WMEAN,
    SUM_N, SUM_X, SUM_Y, SUM_XX, SUM_YY, SUM_XY,
    DATE, DDAYS, DMY_MDY, CAL_360,
    ACCINT, YTM, PRICE, CPN, CALL, SETDATE, MATDATE, SEMI_ANN,
    UNITS, SP, VC, FC, PROFIT,
    SL, SOYD, DB,
    DISP, RADIX, MUTE, HELP, ALG_CHAIN,
}

enum class Shift { NONE, GOLD, AQUA }

enum class KeyKind { FINANCE, FUNCTION, DIGIT, OPERATOR, CLEAR, SHIFT_GOLD, SHIFT_AQUA }

/**
 * One key: its main function plus the two shifted ones.
 * The gold label is printed above the key, the aqua label below it.
 */
data class KeyDef(
    val key: Key,
    val kind: KeyKind,
    val label: String,
    val main: Fn?,
    val goldLabel: String? = null,
    val gold: Fn? = null,
    val aquaLabel: String? = null,
    val aqua: Fn? = null,
)

object KeyMap {
    const val COLUMNS = 5

    val keys: List<KeyDef> = listOf(
        KeyDef(Key.N, KeyKind.FINANCE, "N", Fn.N, "AccInt", Fn.ACCINT, "×P/YR", Fn.XPYR),
        KeyDef(Key.IYR, KeyKind.FINANCE, "I/YR", Fn.IYR, "YTM", Fn.YTM, "NOM%", Fn.NOM),
        KeyDef(Key.PV, KeyKind.FINANCE, "PV", Fn.PV, "PRICE", Fn.PRICE, "EFF%", Fn.EFF),
        KeyDef(Key.PMT, KeyKind.FINANCE, "PMT", Fn.PMT, "CPN%", Fn.CPN, "P/YR", Fn.PYR),
        KeyDef(Key.FV, KeyKind.FINANCE, "FV", Fn.FV, "CALL", Fn.CALL, "AMORT", Fn.AMORT),

        KeyDef(Key.INPUT, KeyKind.FUNCTION, "INPUT", Fn.INPUT, "DMY/MDY", Fn.DMY_MDY, "DATE", Fn.DATE),
        KeyDef(Key.MU, KeyKind.FUNCTION, "MU", Fn.MU, "360/Act", Fn.CAL_360, "ΔDAYS", Fn.DDAYS),
        KeyDef(Key.CST, KeyKind.FUNCTION, "CST", Fn.CST, "Semi/Ann", Fn.SEMI_ANN, "IRR/YR", Fn.IRR),
        KeyDef(Key.PRC, KeyKind.FUNCTION, "PRC", Fn.PRC, "SetDate", Fn.SETDATE, "NPV", Fn.NPV),
        KeyDef(Key.MAR, KeyKind.FUNCTION, "MAR", Fn.MAR, "MatDate", Fn.MATDATE, "Beg/End", Fn.BEG_END),

        KeyDef(Key.K, KeyKind.FUNCTION, "K", Fn.K, "UNITS", Fn.UNITS, "SWAP", Fn.SWAP),
        KeyDef(Key.PCT, KeyKind.FUNCTION, "%", Fn.PCT, "SP", Fn.SP, "%CHG", Fn.PCT_CHG),
        KeyDef(Key.CFJ, KeyKind.FUNCTION, "CFj", Fn.CFJ, "VC", Fn.VC, "Nj", Fn.NJ),
        KeyDef(Key.SIGMA, KeyKind.FUNCTION, "Σ+", Fn.SIGMA_PLUS, "FC", Fn.FC, "Σ−", Fn.SIGMA_MINUS),
        KeyDef(Key.BACK, KeyKind.FUNCTION, "←", Fn.BACK, "PROFIT", Fn.PROFIT, "RND", Fn.RND),

        KeyDef(Key.CHS, KeyKind.FUNCTION, "+/−", Fn.CHS, "SL", Fn.SL, "E", Fn.EEX),
        KeyDef(Key.RCL, KeyKind.FUNCTION, "RCL", Fn.RCL, "SOYD", Fn.SOYD, "STO", Fn.STO),
        KeyDef(Key.TO_M, KeyKind.FUNCTION, "→M", Fn.TO_M, "DB", Fn.DB, "C STAT", Fn.CLR_STAT),
        KeyDef(Key.RM, KeyKind.FUNCTION, "RM", Fn.RM, "HYP", Fn.HYP, "(", Fn.LPAREN),
        KeyDef(Key.M_PLUS, KeyKind.FUNCTION, "M+", Fn.M_PLUS, "INV", Fn.INV, ")", Fn.RPAREN),

        KeyDef(Key.AQUA, KeyKind.SHIFT_AQUA, "", null),
        KeyDef(Key.D7, KeyKind.DIGIT, "7", Fn.D7, "Σx²", Fn.SUM_XX, "x̄,ȳ", Fn.MEAN),
        KeyDef(Key.D8, KeyKind.DIGIT, "8", Fn.D8, "Σy²", Fn.SUM_YY, "Sx,Sy", Fn.SDEV),
        KeyDef(Key.D9, KeyKind.DIGIT, "9", Fn.D9, "Σxy", Fn.SUM_XY, "σx,σy", Fn.PSDEV),
        KeyDef(Key.DIV, KeyKind.OPERATOR, "÷", Fn.DIV, "SIN", Fn.SIN, "1/x", Fn.INV_X),

        KeyDef(Key.GOLD, KeyKind.SHIFT_GOLD, "", null),
        KeyDef(Key.D4, KeyKind.DIGIT, "4", Fn.D4, "n", Fn.SUM_N, "x̂,r", Fn.EST_X),
        KeyDef(Key.D5, KeyKind.DIGIT, "5", Fn.D5, "Σx", Fn.SUM_X, "ŷ,m", Fn.EST_Y),
        KeyDef(Key.D6, KeyKind.DIGIT, "6", Fn.D6, "Σy", Fn.SUM_Y, "x̄w,b", Fn.WMEAN),
        KeyDef(Key.MUL, KeyKind.OPERATOR, "×", Fn.MUL, "COS", Fn.COS, "yˣ", Fn.POW),

        KeyDef(Key.CLR, KeyKind.CLEAR, "C", Fn.CLR, null, null, "C ALL", Fn.CLR_ALL),
        KeyDef(Key.D1, KeyKind.DIGIT, "1", Fn.D1, "Rad/Deg", Fn.RAD_DEG, "eˣ", Fn.EXP),
        KeyDef(Key.D2, KeyKind.DIGIT, "2", Fn.D2, "nPr", Fn.NPR, "LN", Fn.LN),
        KeyDef(Key.D3, KeyKind.DIGIT, "3", Fn.D3, "nCr", Fn.NCR, "n!", Fn.FACT),
        KeyDef(Key.SUB, KeyKind.OPERATOR, "−", Fn.SUB, "TAN", Fn.TAN, "√x", Fn.SQRT),

        KeyDef(Key.HELP, KeyKind.CLEAR, "HELP", Fn.HELP, null, null, "MUTE", Fn.MUTE),
        KeyDef(Key.D0, KeyKind.DIGIT, "0", Fn.D0, "RAND", Fn.RAND, "π", Fn.PI),
        KeyDef(Key.DOT, KeyKind.DIGIT, ".", Fn.DOT, "Alg/Chain", Fn.ALG_CHAIN, ",/.", Fn.RADIX),
        KeyDef(Key.EQ, KeyKind.OPERATOR, "=", Fn.EQ, null, null, "DISP", Fn.DISP),
        KeyDef(Key.ADD, KeyKind.OPERATOR, "+", Fn.ADD, null, null, "x²", Fn.SQUARE),
    )

    private val byKey = keys.associateBy { it.key }

    fun of(key: Key): KeyDef = byKey.getValue(key)
}
