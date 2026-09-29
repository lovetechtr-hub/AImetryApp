package com.djmetry.data.analytics

// Сгенерировано из AImetry/src/utils/countryCoords.ts (как на сайте) и точек подписей Natural Earth 110m для остальных стран.
// Не править руками.

/** Центр страны для пузыря на карте: ISO2 → (широта, долгота). */
internal val COUNTRY_CENTROIDS: Map<String, Pair<Double, Double>> = mapOf(
    "AD" to (42.5 to 1.5), "AE" to (24.0 to 54.0), "AF" to (33.0 to 65.0), "AL" to (41.0 to 20.0), "AM" to (40.0 to 45.0), "AO" to (-12.5 to 18.5),
    "AQ" to (-79.84 to 35.89), "AR" to (-34.0 to -64.0), "AT" to (47.33 to 13.33), "AU" to (-27.0 to 133.0), "AZ" to (40.5 to 47.5), "BA" to (44.0 to 18.0),
    "BD" to (24.21 to 89.68), "BE" to (50.83 to 4.0), "BF" to (12.67 to -1.36), "BG" to (43.0 to 25.0), "BH" to (26.0 to 50.55), "BI" to (-3.33 to 29.92),
    "BJ" to (10.32 to 2.35), "BN" to (4.45 to 114.55), "BO" to (-16.67 to -64.59), "BR" to (-10.0 to -55.0), "BS" to (26.4 to -77.15), "BT" to (27.54 to 90.04),
    "BW" to (-22.1 to 24.18), "BY" to (53.0 to 28.0), "BZ" to (17.2 to -88.71), "CA" to (60.0 to -95.0), "CD" to (-1.86 to 23.46), "CF" to (6.99 to 20.91),
    "CG" to (0.14 to 15.9), "CH" to (47.0 to 8.0), "CI" to (7.49 to -5.57), "CL" to (-30.0 to -71.0), "CM" to (4.59 to 12.47), "CN" to (35.0 to 105.0),
    "CO" to (4.0 to -72.0), "CR" to (10.0 to -84.0), "CU" to (21.33 to -77.98), "CY" to (35.0 to 33.0), "CZ" to (49.75 to 15.5), "DE" to (51.0 to 9.0),
    "DJ" to (11.98 to 42.5), "DK" to (56.0 to 10.0), "DO" to (19.0 to -70.67), "DZ" to (28.0 to 3.0), "EC" to (-2.0 to -77.5), "EE" to (59.0 to 26.0),
    "EG" to (27.0 to 30.0), "EH" to (23.97 to -12.63), "ER" to (15.79 to 38.29), "ES" to (40.0 to -4.0), "ET" to (8.03 to 39.09), "FI" to (64.0 to 26.0),
    "FJ" to (-17.83 to 177.98), "FK" to (-51.61 to -58.74), "FR" to (46.0 to 2.0), "GA" to (-0.44 to 11.84), "GB" to (54.0 to -2.0), "GE" to (42.0 to 43.5),
    "GH" to (7.72 to -1.04), "GL" to (74.32 to -39.34), "GM" to (13.64 to -15.0), "GN" to (10.62 to -10.02), "GQ" to (2.33 to 8.99), "GR" to (39.0 to 22.0),
    "GT" to (14.98 to -90.5), "GW" to (12.16 to -14.52), "GY" to (5.12 to -58.94), "HK" to (22.25 to 114.17), "HN" to (14.79 to -86.89), "HR" to (45.17 to 15.5),
    "HT" to (19.26 to -72.22), "HU" to (47.0 to 20.0), "ID" to (-5.0 to 120.0), "IE" to (53.0 to -8.0), "IL" to (31.5 to 34.75), "IN" to (20.0 to 77.0),
    "IQ" to (33.09 to 43.26), "IR" to (32.0 to 53.0), "IS" to (65.0 to -18.0), "IT" to (42.83 to 12.83), "JM" to (18.14 to -77.32), "JO" to (30.81 to 36.38),
    "JP" to (36.0 to 138.0), "KE" to (1.0 to 38.0), "KG" to (41.67 to 74.53), "KH" to (12.65 to 104.5), "KP" to (39.89 to 126.44), "KR" to (37.0 to 127.5),
    "KW" to (29.34 to 47.66), "KZ" to (48.0 to 68.0), "LA" to (19.43 to 102.53), "LB" to (33.83 to 35.83), "LK" to (7.58 to 80.7), "LR" to (6.45 to -9.46),
    "LS" to (-29.48 to 28.25), "LT" to (56.0 to 24.0), "LU" to (49.75 to 6.17), "LV" to (57.0 to 25.0), "LY" to (26.64 to 18.01), "MA" to (32.0 to -5.0),
    "MC" to (43.73 to 7.4), "MD" to (47.0 to 29.0), "ME" to (42.0 to 19.0), "MG" to (-18.63 to 46.7), "MK" to (41.83 to 22.0), "ML" to (18.69 to -2.04),
    "MM" to (21.57 to 95.8), "MN" to (46.0 to 104.15), "MR" to (19.59 to -9.74), "MT" to (35.83 to 14.58), "MW" to (-13.39 to 33.61), "MX" to (23.0 to -102.0),
    "MY" to (2.5 to 112.5), "MZ" to (-13.94 to 37.84), "NA" to (-20.58 to 17.11), "NC" to (-21.06 to 165.08), "NE" to (17.45 to 9.5), "NG" to (10.0 to 8.0),
    "NI" to (12.67 to -85.07), "NL" to (52.5 to 5.75), "NO" to (62.0 to 10.0), "NP" to (28.3 to 83.64), "NZ" to (-41.0 to 174.0), "OM" to (21.0 to 57.0),
    "PA" to (9.0 to -80.0), "PE" to (-10.0 to -76.0), "PG" to (-5.7 to 143.91), "PH" to (13.0 to 122.0), "PK" to (29.33 to 68.55), "PL" to (52.0 to 20.0),
    "PR" to (18.23 to -66.48), "PS" to (32.05 to 35.29), "PT" to (39.5 to -8.0), "PY" to (-21.67 to -60.15), "QA" to (25.5 to 51.25), "RO" to (46.0 to 25.0),
    "RS" to (44.0 to 21.0), "RU" to (60.0 to 100.0), "RW" to (-1.9 to 30.1), "SA" to (25.0 to 45.0), "SB" to (-8.03 to 159.17), "SD" to (16.33 to 29.26),
    "SE" to (62.0 to 15.0), "SG" to (1.37 to 103.8), "SI" to (46.0 to 15.0), "SK" to (48.67 to 19.5), "SL" to (8.62 to -11.76), "SN" to (15.14 to -14.78),
    "SO" to (3.57 to 45.19), "SR" to (4.14 to -55.91), "SS" to (7.23 to 30.39), "SV" to (13.69 to -88.89), "SY" to (35.01 to 38.28), "SZ" to (-26.53 to 31.47),
    "TD" to (15.14 to 18.65), "TF" to (-49.3 to 69.12), "TG" to (8.81 to 1.06), "TH" to (15.0 to 100.0), "TJ" to (38.2 to 72.59), "TL" to (-8.8 to 125.85),
    "TM" to (39.86 to 58.68), "TN" to (34.0 to 9.0), "TR" to (39.0 to 35.0), "TT" to (11.0 to -60.92), "TW" to (23.65 to 120.87), "TZ" to (-6.05 to 34.96),
    "UA" to (49.0 to 32.0), "UG" to (1.97 to 32.95), "US" to (38.0 to -97.0), "UY" to (-33.0 to -56.0), "UZ" to (41.0 to 64.0), "VE" to (8.0 to -66.0),
    "VN" to (16.0 to 106.0), "VU" to (-15.37 to 166.91), "XK" to (42.59 to 20.86), "YE" to (15.33 to 45.87), "ZA" to (-29.0 to 24.0), "ZM" to (-15.0 to 30.0),
    "ZW" to (-20.0 to 30.0),
)
