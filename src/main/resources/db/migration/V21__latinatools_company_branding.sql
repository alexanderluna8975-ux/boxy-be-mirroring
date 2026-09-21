-- Matches the frontend's LATINATOOLS_TENANT (tenants.config.ts) — the same Orange 600/700/50
-- scale already used app-wide as --color-action-primary, and the tagline shown on the reference
-- printed Cotización layout.
-- Currency corrected to Bolivianos (BOB/Bs) — the reference printed Cotización shows "Bs
-- 10,400.00", not the previously-seeded USD "$" default from V2's initial seed.
-- Mixed-case brand name ("LatinaTools", not V14's ALL-CAPS "LATINATOOLS") — matches the
-- reference logo/wordmark casing, printed as-is in the PDF letterhead and signature lines.
UPDATE companies
SET name = 'LatinaTools',
    trade_name = 'LatinaTools',
    primary_color = '#e8590c',
    primary_hover = '#c2470a',
    primary_subtle_bg = '#fff7ed',
    slogan = 'Distribuidora de herramientas y equipo de construcción',
    currency_code = 'BOB',
    currency_symbol = 'Bs'
WHERE id = 1;
