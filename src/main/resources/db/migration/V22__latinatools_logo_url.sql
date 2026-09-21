-- Real hosted logo asset, matching the frontend's LATINATOOLS_TENANT.branding.logoUrl
-- (tenants.config.ts) — now shows on the PDF letterhead's brand-mark image too.
UPDATE companies
SET logo_url = 'https://res.cloudinary.com/bq5etkzd/image/upload/v1790006955/brand-logo.png'
WHERE id = 1;
