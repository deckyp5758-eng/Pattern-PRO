package com.example.domain

import com.example.model.CompleteHubAnalysis

data class Mql5ConfigParams(
    val brokerSuffix: String = "",
    val emaFast: Int = 20,
    val emaMid: Int = 50,
    val emaSlow: Int = 200,
    val rsiPeriod: Int = 14,
    val adxPeriod: Int = 14,
    val atrPeriod: Int = 14,
    val volSmaPeriod: Int = 20,
    val swingStrength: Int = 3,
    val enableNewsWebRequest: Boolean = true
)

object Mql5IndicatorCodeGenerator {

    fun generateMq5Source(
        config: Mql5ConfigParams,
        analysis: CompleteHubAnalysis
    ): String {
        return """
//+------------------------------------------------------------------+
//|                                 HUB_Smart_Pattern_Engine_Pro.mq5 |
//|                     HUB Smart Pattern Engine Pro - Analytical V1 |
//|        NON-REPAINT • CLOSED CANDLE [INDEX 1] • NO AUTO-TRADING   |
//+------------------------------------------------------------------+
#property copyright "HUB Quantitative Systems"
#property version   "1.00"
#property indicator_chart_window
#property indicator_buffers 5
#property indicator_plots   5

//--- Plot 1: EMA ${config.emaFast}
#property indicator_label1  "EMA_${config.emaFast}"
#property indicator_type1   DRAW_LINE
#property indicator_color1  clrGold
#property indicator_style1  STYLE_SOLID
#property indicator_width1  2

//--- Plot 2: EMA ${config.emaMid}
#property indicator_label2  "EMA_${config.emaMid}"
#property indicator_type2   DRAW_LINE
#property indicator_color2  clrDeepSkyBlue
#property indicator_style2  STYLE_SOLID
#property indicator_width2  2

//--- Plot 3: EMA ${config.emaSlow}
#property indicator_label3  "EMA_${config.emaSlow}"
#property indicator_type3   DRAW_LINE
#property indicator_color3  clrCrimson
#property indicator_style3  STYLE_DOT
#property indicator_width3  2

//--- Plot 4: Confirmed Closed-Candle Bullish Signal [Index >= 1 ONLY]
#property indicator_label4  "Closed_Bull_Signal"
#property indicator_type4   DRAW_ARROW
#property indicator_color4  clrMediumSpringGreen
#property indicator_width4  2

//--- Plot 5: Confirmed Closed-Candle Bearish Signal [Index >= 1 ONLY]
#property indicator_label5  "Closed_Bear_Signal"
#property indicator_type5   DRAW_ARROW
#property indicator_color5  clrTomato
#property indicator_width5  2

//====================================================================
// INPUT PARAMETERS
//====================================================================
input string InpBrokerSuffix      = "${config.brokerSuffix}"; // Target Suffix (e.g. .sml for XAUUSD.sml / EURUSD.sml)
input int    InpEmaFast           = ${config.emaFast};        // Trend Engine Fast EMA
input int    InpEmaMid            = ${config.emaMid};         // Trend Engine Mid EMA
input int    InpEmaSlow           = ${config.emaSlow};        // Trend Engine Slow EMA
input int    InpRsiPeriod         = ${config.rsiPeriod};      // Momentum RSI Period (>55 Bull, <45 Bear)
input int    InpAdxPeriod         = ${config.adxPeriod};      // Momentum ADX Period (>25 Trend Confirmed)
input int    InpAtrPeriod         = ${config.atrPeriod};      // Volatility ATR Period
input int    InpVolSmaPeriod      = ${config.volSmaPeriod};   // Volume Confluence SMA Period
input int    InpSwingPivotBars    = ${config.swingStrength};  // Confirmed Structure Pivot Left/Right Bars
input bool   InpEnableNewsEngine  = ${config.enableNewsWebRequest}; // Enable Live Macro News WebRequest
input string InpNewsCalendarUrl   = "https://nfs.faireconomy.media/ff_calendar_thisweek.xml";

//====================================================================
// INDICATOR BUFFERS & HANDLES
//====================================================================
double ExtEmaFastBuffer[];
double ExtEmaMidBuffer[];
double ExtEmaSlowBuffer[];
double ExtBullSignalBuffer[];
double ExtBearSignalBuffer[];

int    hEmaFast, hEmaMid, hEmaSlow;
int    hRsi, hMacd, hAdx, hAtr;
datetime g_lastClosedBarTime = 0;
string   g_latestNewsStatus  = "USD / Fed / FOMC / CPI / ECB Active Monitor";
string   g_hudPrefix         = "HUB_SPE_HUD_";

//+------------------------------------------------------------------+
//| Custom indicator initialization function                         |
//+------------------------------------------------------------------+
int OnInit()
{
   SetIndexBuffer(0, ExtEmaFastBuffer, INDICATOR_DATA);
   SetIndexBuffer(1, ExtEmaMidBuffer,  INDICATOR_DATA);
   SetIndexBuffer(2, ExtEmaSlowBuffer, INDICATOR_DATA);
   SetIndexBuffer(3, ExtBullSignalBuffer, INDICATOR_DATA);
   SetIndexBuffer(4, ExtBearSignalBuffer, INDICATOR_DATA);

   PlotIndexSetInteger(3, PLOT_ARROW, 233); // Up Arrow on Closed Bar
   PlotIndexSetInteger(4, PLOT_ARROW, 234); // Down Arrow on Closed Bar
   PlotIndexSetDouble(3, PLOT_EMPTY_VALUE, 0.0);
   PlotIndexSetDouble(4, PLOT_EMPTY_VALUE, 0.0);

   ArraySetAsSeries(ExtEmaFastBuffer, true);
   ArraySetAsSeries(ExtEmaMidBuffer,  true);
   ArraySetAsSeries(ExtEmaSlowBuffer, true);
   ArraySetAsSeries(ExtBullSignalBuffer, true);
   ArraySetAsSeries(ExtBearSignalBuffer, true);

   hEmaFast = iMA(_Symbol, _Period, InpEmaFast, 0, MODE_EMA, PRICE_CLOSE);
   hEmaMid  = iMA(_Symbol, _Period, InpEmaMid,  0, MODE_EMA, PRICE_CLOSE);
   hEmaSlow = iMA(_Symbol, _Period, InpEmaSlow, 0, MODE_EMA, PRICE_CLOSE);
   hRsi     = iRSI(_Symbol, _Period, InpRsiPeriod, PRICE_CLOSE);
   hMacd    = iMACD(_Symbol, _Period, 12, 26, 9, PRICE_CLOSE);
   hAdx     = iADX(_Symbol, _Period, InpAdxPeriod);
   hAtr     = iATR(_Symbol, _Period, InpAtrPeriod);

   if(hEmaFast == INVALID_HANDLE || hEmaMid == INVALID_HANDLE || hEmaSlow == INVALID_HANDLE ||
      hRsi == INVALID_HANDLE || hMacd == INVALID_HANDLE || hAdx == INVALID_HANDLE || hAtr == INVALID_HANDLE)
   {
      Print("HUB Smart Pattern Engine Pro: Error creating indicator handles.");
      return(INIT_FAILED);
   }

   EventSetTimer(60); // Poll News Engine every 60s
   CreateHudPanel();
   return(INIT_SUCCEEDED);
}

//+------------------------------------------------------------------+
//| Deinitialization                                                 |
//+------------------------------------------------------------------+
void OnDeinit(const int reason)
{
   EventKillTimer();
   ObjectsDeleteAll(0, g_hudPrefix);
}

//+------------------------------------------------------------------+
//| 3. CANDLE ENGINE: 15 Closed-Candle Pattern Detector (idx >= 1)   |
//+------------------------------------------------------------------+
string DetectClosedCandlePattern(const MqlRates &rates[], int i, double atr, int &outBias)
{
   outBias = 0;
   if(i < 1) return "FORMING_BAR_0_IGNORED"; // NEVER evaluate final pattern on bar 0!

   double o1 = rates[i].open,   h1 = rates[i].high,   l1 = rates[i].low,   c1 = rates[i].close;
   double o2 = rates[i+1].open, h2 = rates[i+1].high, l2 = rates[i+1].low, c2 = rates[i+1].close;
   double o3 = rates[i+2].open, h3 = rates[i+2].high, l3 = rates[i+2].low, c3 = rates[i+2].close;

   double body1 = MathAbs(c1 - o1);
   double body2 = MathAbs(c2 - o2);
   double body3 = MathAbs(c3 - o3);
   double range1 = MathMax(h1 - l1, _Point);
   double upper1 = h1 - MathMax(o1, c1);
   double lower1 = MathMin(o1, c1) - l1;
   double ratio1 = body1 / range1;
   bool bull1 = (c1 > o1), bear1 = (c1 < o1);
   bool bull2 = (c2 > o2), bear2 = (c2 < o2);
   bool bull3 = (c3 > o3), bear3 = (c3 < o3);
   double tol = atr * 0.08;

   // 14 & 15: Three White Soldiers / Three Black Crows
   if(bull3 && bull2 && bull1 && c1 > c2 && c2 > c3 && ratio1 > 0.50) { outBias = 1;  return "Three White Soldiers"; }
   if(bear3 && bear2 && bear1 && c1 < c2 && c2 < c3 && ratio1 > 0.50) { outBias = -1; return "Three Black Crows"; }

   // 12 & 13: Morning Star / Evening Star
   double mid3 = (o3 + c3) * 0.5;
   if(bear3 && body2 < body3 * 0.45 && bull1 && c1 >= mid3) { outBias = 1;  return "Morning Star"; }
   if(bull3 && body2 < body3 * 0.45 && bear1 && c1 <= mid3) { outBias = -1; return "Evening Star"; }

   // 6 & 7: Bullish / Bearish Engulfing
   if(bull1 && bear2 && c1 >= o2 && o1 <= c2 + tol && body1 > body2) { outBias = 1;  return "Bullish Engulfing"; }
   if(bear1 && bull2 && c1 <= o2 && o1 >= c2 - tol && body1 > body2) { outBias = -1; return "Bearish Engulfing"; }

   // 8 & 9: Bullish / Bearish Harami
   if(bear2 && bull1 && o1 > c2 && c1 < o2 && body1 <= body2 * 0.65) { outBias = 1;  return "Bullish Harami"; }
   if(bull2 && bear1 && o1 < c2 && c1 > o2 && body1 <= body2 * 0.65) { outBias = -1; return "Bearish Harami"; }

   // 10 & 11: Tweezer Top / Tweezer Bottom
   if(bull2 && bear1 && MathAbs(h1 - h2) <= tol) { outBias = -1; return "Tweezer Top"; }
   if(bear2 && bull1 && MathAbs(l1 - l2) <= tol) { outBias = 1;  return "Tweezer Bottom"; }

   // 2, 3, 4, 5: Hammer, Inverted Hammer, Shooting Star, Hanging Man
   bool priorDown = (c2 < rates[i+4].close);
   bool priorUp   = (c2 > rates[i+4].close);
   if(lower1 >= body1 * 1.8 && upper1 <= body1 * 0.6 && priorDown) { outBias = 1;  return "Hammer"; }
   if(upper1 >= body1 * 1.8 && lower1 <= body1 * 0.6 && priorDown) { outBias = 1;  return "Inverted Hammer"; }
   if(upper1 >= body1 * 1.8 && lower1 <= body1 * 0.6 && priorUp)   { outBias = -1; return "Shooting Star"; }
   if(lower1 >= body1 * 1.8 && upper1 <= body1 * 0.6 && priorUp && bear1) { outBias = -1; return "Hanging Man"; }

   // 1: Doji
   if(ratio1 <= 0.11) { outBias = 0; return "Doji"; }

   return bull1 ? "Bullish Closed" : "Bearish Closed";
}

//+------------------------------------------------------------------+
//| Main Calculation Loop (Strictly Non-Repaint on Closed Bars)      |
//+------------------------------------------------------------------+
int OnCalculate(const int rates_total,
                const int prev_calculated,
                const datetime &time[],
                const double &open[],
                const double &high[],
                const double &low[],
                const double &close[],
                const long &tick_volume[],
                const long &volume[],
                const int &spread[])
{
   if(rates_total < 210) return(0);

   MqlRates rates[];
   ArraySetAsSeries(rates, true);
   int copied = CopyRates(_Symbol, _Period, 0, 210, rates);
   if(copied < 205) return(0);

   CopyBuffer(hEmaFast, 0, 0, copied, ExtEmaFastBuffer);
   CopyBuffer(hEmaMid,  0, 0, copied, ExtEmaMidBuffer);
   CopyBuffer(hEmaSlow, 0, 0, copied, ExtEmaSlowBuffer);

   double rsiBuf[], macdMain[], macdSig[], adxVal[], plusDi[], minusDi[], atrBuf[];
   ArraySetAsSeries(rsiBuf, true);
   ArraySetAsSeries(macdMain, true);
   ArraySetAsSeries(macdSig, true);
   ArraySetAsSeries(adxVal, true);
   ArraySetAsSeries(plusDi, true);
   ArraySetAsSeries(minusDi, true);
   ArraySetAsSeries(atrBuf, true);

   CopyBuffer(hRsi,  0, 0, 100, rsiBuf);
   CopyBuffer(hMacd, 0, 0, 100, macdMain);
   CopyBuffer(hMacd, 1, 0, 100, macdSig);
   CopyBuffer(hAdx,  0, 0, 100, adxVal);
   CopyBuffer(hAdx,  1, 0, 100, plusDi);
   CopyBuffer(hAdx,  2, 0, 100, minusDi);
   CopyBuffer(hAtr,  0, 0, 100, atrBuf);

   // Clear arrow on forming bar 0 ALWAYS (Principle 1: Never plot final signal on Bar 0)
   ExtBullSignalBuffer[0] = 0.0;
   ExtBearSignalBuffer[0] = 0.0;

   // Evaluate only newly closed bars (index >= 1)
   if(rates[1].time != g_lastClosedBarTime)
   {
      g_lastClosedBarTime = rates[1].time;
      for(int i = 1; i < 60; i++)
      {
         int patBias = 0;
         string patName = DetectClosedCandlePattern(rates, i, atrBuf[i], patBias);
         bool emaBull = (ExtEmaFastBuffer[i] > ExtEmaMidBuffer[i] && ExtEmaMidBuffer[i] > ExtEmaSlowBuffer[i]);
         bool emaBear = (ExtEmaFastBuffer[i] < ExtEmaMidBuffer[i] && ExtEmaMidBuffer[i] < ExtEmaSlowBuffer[i]);
         bool rsiBull = (rsiBuf[i] > 55.0);
         bool rsiBear = (rsiBuf[i] < 45.0);
         bool adxOk   = (adxVal[i] > 25.0);

         // Volume SMA 20 check
         double sumVol = 0;
         for(int v = i; v < i + InpVolSmaPeriod; v++) sumVol += (double)rates[v].tick_volume;
         double smaVol = sumVol / InpVolSmaPeriod;
         bool volExp = ((double)rates[i].tick_volume > smaVol);

         if(patBias == 1 && emaBull && (rsiBull || adxOk || volExp))
            ExtBullSignalBuffer[i] = rates[i].low - (atrBuf[i] * 0.45);
         else
            ExtBullSignalBuffer[i] = 0.0;

         if(patBias == -1 && emaBear && (rsiBear || adxOk || volExp))
            ExtBearSignalBuffer[i] = rates[i].high + (atrBuf[i] * 0.45);
         else
            ExtBearSignalBuffer[i] = 0.0;
      }
   }

   // Update HUD with Live Forming Bar [0] monitor & Locked Closed Bar [1] Verdict
   int bar1Bias = 0;
   string bar1Pattern = DetectClosedCandlePattern(rates, 1, atrBuf[1], bar1Bias);
   UpdateHudPanel(rates[0], rates[1], rsiBuf[1], adxVal[1], atrBuf[1], bar1Pattern);

   return(rates_total);
}

//+------------------------------------------------------------------+
//| HUD Creation & Update                                            |
//+------------------------------------------------------------------+
void CreateHudPanel()
{
   string bg = g_hudPrefix + "BG";
   ObjectCreate(0, bg, OBJ_RECTANGLE_LABEL, 0, 0, 0);
   ObjectSetInteger(0, bg, OBJPROP_XDISTANCE, 14);
   ObjectSetInteger(0, bg, OBJPROP_YDISTANCE, 28);
   ObjectSetInteger(0, bg, OBJPROP_XSIZE, 380);
   ObjectSetInteger(0, bg, OBJPROP_YSIZE, 210);
   ObjectSetInteger(0, bg, OBJPROP_BGCOLOR, C'11,16,30');
   ObjectSetInteger(0, bg, OBJPROP_BORDER_COLOR, C'245,176,65');
}

void UpdateHudPanel(const MqlRates &bar0, const MqlRates &bar1, double rsi1, double adx1, double atr1, string pat1)
{
   Comment(StringFormat(
      "=== HUB SMART PATTERN ENGINE PRO [NON-REPAINT] ===\n"
      "Symbol: %s | TF: %s | Spread: %d pts\n"
      "[BAR 0 FORMING MONITOR] Price: %.*f | TickVol: %d\n"
      "[BAR 1 CLOSED SIGNAL]   Close: %.*f | Pattern: %s\n"
      "Trend Stack: %s | RSI(14): %.1f | ADX(14): %.1f\n"
      "ATR(14): %.*f | Stop Ref (1.5x ATR): %.*f\n"
      "MTF Synthesis: %s\n"
      "News Engine: %s",
      _Symbol, EnumToString(_Period), bar0.spread,
      _Digits, bar0.close, bar0.tick_volume,
      _Digits, bar1.close, pat1,
      "${analysis.trendEngine.classification.label}", rsi1, adx1,
      _Digits, atr1, _Digits, atr1 * 1.5,
      "${analysis.mtfEngine.ltfState}",
      g_latestNewsStatus
   ));
}
//+------------------------------------------------------------------+
        """.trimIndent()
    }
}
