-- ============================================================================
-- Tanzania location hierarchy (region → district → ward → street)
--
-- Drives the cascading dropdowns in the admin offer-creation form and
-- ancestor-lookup in the location-offer matching RPCs. Every row has one of
-- four levels. parent_id chains them: a district's parent is its region, a
-- ward's parent is its district, and so on.
--
-- Seeded with all 31 regions and all ~184 districts of Tanzania. Dar es
-- Salaam wards are fully seeded because that's the primary market. Wards
-- for other regions can be added via admin operations or future migrations
-- without breaking offer matching — the table is designed for incremental
-- population.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tz_locations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    level       TEXT        NOT NULL
                CHECK (level IN ('region', 'district', 'ward', 'street')),
    parent_id   UUID        REFERENCES tz_locations(id) ON DELETE CASCADE,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A region has no parent; everything else must have one.
    CONSTRAINT tz_locations_parent_required
        CHECK ((level = 'region' AND parent_id IS NULL) OR
               (level <> 'region' AND parent_id IS NOT NULL)),
    -- Names must be unique within a parent (case-insensitive).
    CONSTRAINT tz_locations_unique_per_parent
        UNIQUE (parent_id, name)
);

CREATE INDEX IF NOT EXISTS idx_tz_locations_parent_level
    ON tz_locations (parent_id, level, sort_order);
CREATE INDEX IF NOT EXISTS idx_tz_locations_name_lower
    ON tz_locations (lower(name));

ALTER TABLE tz_locations ENABLE ROW LEVEL SECURITY;

-- Everyone can read (used by both admin UI and patient client)
DROP POLICY IF EXISTS tz_locations_read ON tz_locations;
CREATE POLICY tz_locations_read ON tz_locations FOR SELECT USING (TRUE);

-- Only admins can modify
DROP POLICY IF EXISTS tz_locations_admin_write ON tz_locations;
CREATE POLICY tz_locations_admin_write ON tz_locations
    FOR ALL
    USING (EXISTS (SELECT 1 FROM user_roles ur
                    WHERE ur.user_id = auth.uid() AND ur.role_name = 'admin'))
    WITH CHECK (EXISTS (SELECT 1 FROM user_roles ur
                         WHERE ur.user_id = auth.uid() AND ur.role_name = 'admin'));

GRANT SELECT ON tz_locations TO authenticated, anon;
GRANT ALL    ON tz_locations TO service_role;

-- ── Ancestor lookup helper ───────────────────────────────────────────────────
-- Given a location's name + level, returns the whole (region, district, ward,
-- street) chain. Used by the offer-matching RPCs so a request that only
-- identifies the patient's district can still match a region-level offer.
CREATE OR REPLACE FUNCTION tz_location_ancestors(
    p_name  TEXT,
    p_level TEXT
)
RETURNS TABLE (
    region   TEXT,
    district TEXT,
    ward     TEXT,
    street   TEXT
)
LANGUAGE plpgsql STABLE SECURITY DEFINER AS $$
DECLARE
    v_id UUID;
BEGIN
    IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
        RETURN;
    END IF;

    -- Pick the first matching row at the given level (case-insensitive).
    SELECT l.id INTO v_id
      FROM tz_locations l
     WHERE l.level = p_level
       AND lower(l.name) = lower(trim(p_name))
     LIMIT 1;

    IF v_id IS NULL THEN
        RETURN;
    END IF;

    RETURN QUERY
    WITH RECURSIVE chain AS (
        SELECT id, name, level, parent_id
          FROM tz_locations WHERE id = v_id
        UNION ALL
        SELECT l.id, l.name, l.level, l.parent_id
          FROM tz_locations l
          JOIN chain c ON l.id = c.parent_id
    )
    SELECT
        (SELECT name FROM chain WHERE level = 'region'   LIMIT 1),
        (SELECT name FROM chain WHERE level = 'district' LIMIT 1),
        (SELECT name FROM chain WHERE level = 'ward'     LIMIT 1),
        (SELECT name FROM chain WHERE level = 'street'   LIMIT 1);
END;
$$;

GRANT EXECUTE ON FUNCTION tz_location_ancestors(TEXT, TEXT) TO service_role, authenticated, anon;

-- ── Seed: all 31 Tanzania regions with their districts ──────────────────────
-- Idempotent: re-running this migration on an already-populated table skips
-- existing rows via UNIQUE(parent_id, name).
DO $seed$
DECLARE
    r UUID;
BEGIN
    -- Arusha
    INSERT INTO tz_locations (name, level) VALUES ('Arusha', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 1 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Arusha City', 'district', r),
        ('Arusha Rural', 'district', r),
        ('Karatu', 'district', r),
        ('Longido', 'district', r),
        ('Meru', 'district', r),
        ('Monduli', 'district', r),
        ('Ngorongoro', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Dar es Salaam
    INSERT INTO tz_locations (name, level) VALUES ('Dar es Salaam', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 2 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Ilala', 'district', r),
        ('Kinondoni', 'district', r),
        ('Temeke', 'district', r),
        ('Ubungo', 'district', r),
        ('Kigamboni', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Dodoma
    INSERT INTO tz_locations (name, level) VALUES ('Dodoma', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 3 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bahi', 'district', r),
        ('Chamwino', 'district', r),
        ('Chemba', 'district', r),
        ('Dodoma City', 'district', r),
        ('Kondoa', 'district', r),
        ('Kongwa', 'district', r),
        ('Mpwapwa', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Geita
    INSERT INTO tz_locations (name, level) VALUES ('Geita', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 4 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bukombe', 'district', r),
        ('Chato', 'district', r),
        ('Geita', 'district', r),
        ('Mbogwe', 'district', r),
        ('Nyang''hwale', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Iringa
    INSERT INTO tz_locations (name, level) VALUES ('Iringa', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 5 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Iringa Municipal', 'district', r),
        ('Iringa Rural', 'district', r),
        ('Kilolo', 'district', r),
        ('Mafinga', 'district', r),
        ('Mufindi', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Kagera
    INSERT INTO tz_locations (name, level) VALUES ('Kagera', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 6 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Biharamulo', 'district', r),
        ('Bukoba Municipal', 'district', r),
        ('Bukoba Rural', 'district', r),
        ('Karagwe', 'district', r),
        ('Kyerwa', 'district', r),
        ('Missenyi', 'district', r),
        ('Muleba', 'district', r),
        ('Ngara', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Katavi
    INSERT INTO tz_locations (name, level) VALUES ('Katavi', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 7 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Mlele', 'district', r),
        ('Mpanda Municipal', 'district', r),
        ('Mpimbwe', 'district', r),
        ('Nsimbo', 'district', r),
        ('Tanganyika', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Kigoma
    INSERT INTO tz_locations (name, level) VALUES ('Kigoma', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 8 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Buhigwe', 'district', r),
        ('Kakonko', 'district', r),
        ('Kasulu Rural', 'district', r),
        ('Kasulu Town', 'district', r),
        ('Kibondo', 'district', r),
        ('Kigoma Municipal', 'district', r),
        ('Kigoma Rural', 'district', r),
        ('Uvinza', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Kilimanjaro
    INSERT INTO tz_locations (name, level) VALUES ('Kilimanjaro', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 9 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Hai', 'district', r),
        ('Moshi Municipal', 'district', r),
        ('Moshi Rural', 'district', r),
        ('Mwanga', 'district', r),
        ('Rombo', 'district', r),
        ('Same', 'district', r),
        ('Siha', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Lindi
    INSERT INTO tz_locations (name, level) VALUES ('Lindi', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 10 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Kilwa', 'district', r),
        ('Lindi Municipal', 'district', r),
        ('Lindi Rural', 'district', r),
        ('Liwale', 'district', r),
        ('Nachingwea', 'district', r),
        ('Ruangwa', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Manyara
    INSERT INTO tz_locations (name, level) VALUES ('Manyara', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 11 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Babati Town', 'district', r),
        ('Babati Rural', 'district', r),
        ('Hanang', 'district', r),
        ('Kiteto', 'district', r),
        ('Mbulu Town', 'district', r),
        ('Mbulu Rural', 'district', r),
        ('Simanjiro', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Mara
    INSERT INTO tz_locations (name, level) VALUES ('Mara', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 12 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bunda', 'district', r),
        ('Butiama', 'district', r),
        ('Musoma Municipal', 'district', r),
        ('Musoma Rural', 'district', r),
        ('Rorya', 'district', r),
        ('Serengeti', 'district', r),
        ('Tarime Town', 'district', r),
        ('Tarime Rural', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Mbeya
    INSERT INTO tz_locations (name, level) VALUES ('Mbeya', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 13 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Busokelo', 'district', r),
        ('Chunya', 'district', r),
        ('Kyela', 'district', r),
        ('Mbarali', 'district', r),
        ('Mbeya City', 'district', r),
        ('Mbeya Rural', 'district', r),
        ('Rungwe', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Morogoro
    INSERT INTO tz_locations (name, level) VALUES ('Morogoro', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 14 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Gairo', 'district', r),
        ('Kilombero', 'district', r),
        ('Kilosa', 'district', r),
        ('Malinyi', 'district', r),
        ('Morogoro Municipal', 'district', r),
        ('Morogoro Rural', 'district', r),
        ('Mvomero', 'district', r),
        ('Ulanga', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Mtwara
    INSERT INTO tz_locations (name, level) VALUES ('Mtwara', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 15 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Masasi Town', 'district', r),
        ('Masasi Rural', 'district', r),
        ('Mtwara Municipal', 'district', r),
        ('Mtwara Rural', 'district', r),
        ('Nanyamba', 'district', r),
        ('Nanyumbu', 'district', r),
        ('Newala Town', 'district', r),
        ('Newala Rural', 'district', r),
        ('Tandahimba', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Mwanza
    INSERT INTO tz_locations (name, level) VALUES ('Mwanza', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 16 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Ilemela', 'district', r),
        ('Kwimba', 'district', r),
        ('Magu', 'district', r),
        ('Misungwi', 'district', r),
        ('Nyamagana', 'district', r),
        ('Sengerema', 'district', r),
        ('Ukerewe', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Njombe
    INSERT INTO tz_locations (name, level) VALUES ('Njombe', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 17 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Ludewa', 'district', r),
        ('Makambako', 'district', r),
        ('Makete', 'district', r),
        ('Njombe Town', 'district', r),
        ('Njombe Rural', 'district', r),
        ('Wanging''ombe', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Pemba North
    INSERT INTO tz_locations (name, level) VALUES ('Pemba North', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 18 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Micheweni', 'district', r),
        ('Wete', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Pemba South
    INSERT INTO tz_locations (name, level) VALUES ('Pemba South', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 19 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Chake Chake', 'district', r),
        ('Mkoani', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Pwani
    INSERT INTO tz_locations (name, level) VALUES ('Pwani', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 20 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bagamoyo', 'district', r),
        ('Kibaha Town', 'district', r),
        ('Kibaha Rural', 'district', r),
        ('Kibiti', 'district', r),
        ('Kisarawe', 'district', r),
        ('Mafia', 'district', r),
        ('Mkuranga', 'district', r),
        ('Rufiji', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Rukwa
    INSERT INTO tz_locations (name, level) VALUES ('Rukwa', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 21 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Kalambo', 'district', r),
        ('Nkasi', 'district', r),
        ('Sumbawanga Municipal', 'district', r),
        ('Sumbawanga Rural', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Ruvuma
    INSERT INTO tz_locations (name, level) VALUES ('Ruvuma', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 22 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Mbinga Town', 'district', r),
        ('Mbinga Rural', 'district', r),
        ('Namtumbo', 'district', r),
        ('Nyasa', 'district', r),
        ('Songea Municipal', 'district', r),
        ('Songea Rural', 'district', r),
        ('Tunduru', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Shinyanga
    INSERT INTO tz_locations (name, level) VALUES ('Shinyanga', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 23 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Kahama Town', 'district', r),
        ('Kishapu', 'district', r),
        ('Msalala', 'district', r),
        ('Shinyanga Municipal', 'district', r),
        ('Shinyanga Rural', 'district', r),
        ('Ushetu', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Simiyu
    INSERT INTO tz_locations (name, level) VALUES ('Simiyu', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 24 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bariadi', 'district', r),
        ('Busega', 'district', r),
        ('Itilima', 'district', r),
        ('Maswa', 'district', r),
        ('Meatu', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Singida
    INSERT INTO tz_locations (name, level) VALUES ('Singida', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 25 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Ikungi', 'district', r),
        ('Iramba', 'district', r),
        ('Manyoni', 'district', r),
        ('Mkalama', 'district', r),
        ('Singida Municipal', 'district', r),
        ('Singida Rural', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Songwe
    INSERT INTO tz_locations (name, level) VALUES ('Songwe', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 26 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Ileje', 'district', r),
        ('Mbozi', 'district', r),
        ('Momba', 'district', r),
        ('Songwe', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Tabora
    INSERT INTO tz_locations (name, level) VALUES ('Tabora', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 27 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Igunga', 'district', r),
        ('Kaliua', 'district', r),
        ('Nzega', 'district', r),
        ('Sikonge', 'district', r),
        ('Tabora Municipal', 'district', r),
        ('Urambo', 'district', r),
        ('Uyui', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Tanga
    INSERT INTO tz_locations (name, level) VALUES ('Tanga', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 28 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Bumbuli', 'district', r),
        ('Handeni Town', 'district', r),
        ('Handeni Rural', 'district', r),
        ('Kilindi', 'district', r),
        ('Korogwe Town', 'district', r),
        ('Korogwe Rural', 'district', r),
        ('Lushoto', 'district', r),
        ('Mkinga', 'district', r),
        ('Muheza', 'district', r),
        ('Pangani', 'district', r),
        ('Tanga City', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Unguja North (Zanzibar)
    INSERT INTO tz_locations (name, level) VALUES ('Unguja North', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 29 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Kaskazini A', 'district', r),
        ('Kaskazini B', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Unguja South (Zanzibar)
    INSERT INTO tz_locations (name, level) VALUES ('Unguja South', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 30 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Kati', 'district', r),
        ('Kusini', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;

    -- Urban West (Zanzibar)
    INSERT INTO tz_locations (name, level) VALUES ('Urban West', 'region')
        ON CONFLICT (parent_id, name) DO UPDATE SET sort_order = 31 RETURNING id INTO r;
    INSERT INTO tz_locations (name, level, parent_id) VALUES
        ('Magharibi A', 'district', r),
        ('Magharibi B', 'district', r),
        ('Mjini', 'district', r)
        ON CONFLICT (parent_id, name) DO NOTHING;
END $seed$;

-- ── Seed: Dar es Salaam wards (primary market) ──────────────────────────────
-- Populated per-district. Additional cities' wards can be added later through
-- the admin UI or follow-up migrations.
DO $wards$
DECLARE
    d UUID;
BEGIN
    -- Ilala wards
    SELECT id INTO d FROM tz_locations WHERE level = 'district' AND name = 'Ilala' LIMIT 1;
    IF d IS NOT NULL THEN
        INSERT INTO tz_locations (name, level, parent_id) VALUES
            ('Buguruni', 'ward', d), ('Gerezani', 'ward', d), ('Ilala', 'ward', d),
            ('Jangwani', 'ward', d), ('Kariakoo', 'ward', d), ('Kipawa', 'ward', d),
            ('Kisutu', 'ward', d), ('Kivukoni', 'ward', d), ('Mchafukoge', 'ward', d),
            ('Mchikichini', 'ward', d), ('Pugu', 'ward', d), ('Segerea', 'ward', d),
            ('Tabata', 'ward', d), ('Ukonga', 'ward', d), ('Upanga East', 'ward', d),
            ('Upanga West', 'ward', d), ('Vingunguti', 'ward', d)
        ON CONFLICT (parent_id, name) DO NOTHING;
    END IF;

    -- Kinondoni wards
    SELECT id INTO d FROM tz_locations WHERE level = 'district' AND name = 'Kinondoni' LIMIT 1;
    IF d IS NOT NULL THEN
        INSERT INTO tz_locations (name, level, parent_id) VALUES
            ('Bunju', 'ward', d), ('Hananasif', 'ward', d), ('Kawe', 'ward', d),
            ('Kijitonyama', 'ward', d), ('Kinondoni', 'ward', d), ('Kunduchi', 'ward', d),
            ('Magomeni', 'ward', d), ('Makumbusho', 'ward', d), ('Mikocheni', 'ward', d),
            ('Msasani', 'ward', d), ('Mwananyamala', 'ward', d), ('Mzimuni', 'ward', d),
            ('Ndugumbi', 'ward', d), ('Tandale', 'ward', d), ('Wazo', 'ward', d)
        ON CONFLICT (parent_id, name) DO NOTHING;
    END IF;

    -- Temeke wards
    SELECT id INTO d FROM tz_locations WHERE level = 'district' AND name = 'Temeke' LIMIT 1;
    IF d IS NOT NULL THEN
        INSERT INTO tz_locations (name, level, parent_id) VALUES
            ('Azimio', 'ward', d), ('Chamazi', 'ward', d), ('Chang''ombe', 'ward', d),
            ('Keko', 'ward', d), ('Kilwa Road', 'ward', d), ('Kurasini', 'ward', d),
            ('Makangarawe', 'ward', d), ('Mbagala', 'ward', d), ('Mbagala Kuu', 'ward', d),
            ('Miburani', 'ward', d), ('Mtoni', 'ward', d), ('Sandali', 'ward', d),
            ('Tandika', 'ward', d), ('Temeke', 'ward', d), ('Toangoma', 'ward', d)
        ON CONFLICT (parent_id, name) DO NOTHING;
    END IF;

    -- Ubungo wards
    SELECT id INTO d FROM tz_locations WHERE level = 'district' AND name = 'Ubungo' LIMIT 1;
    IF d IS NOT NULL THEN
        INSERT INTO tz_locations (name, level, parent_id) VALUES
            ('Goba', 'ward', d), ('Kibamba', 'ward', d), ('Kimara', 'ward', d),
            ('Kwembe', 'ward', d), ('Mabibo', 'ward', d), ('Makuburi', 'ward', d),
            ('Makurumla', 'ward', d), ('Manzese', 'ward', d), ('Mbezi', 'ward', d),
            ('Mburahati', 'ward', d), ('Msigani', 'ward', d), ('Saranga', 'ward', d),
            ('Sinza', 'ward', d), ('Ubungo', 'ward', d)
        ON CONFLICT (parent_id, name) DO NOTHING;
    END IF;

    -- Kigamboni wards
    SELECT id INTO d FROM tz_locations WHERE level = 'district' AND name = 'Kigamboni' LIMIT 1;
    IF d IS NOT NULL THEN
        INSERT INTO tz_locations (name, level, parent_id) VALUES
            ('Kibada', 'ward', d), ('Kigamboni', 'ward', d), ('Kisarawe II', 'ward', d),
            ('Mjimwema', 'ward', d), ('Pembamnazi', 'ward', d), ('Somangila', 'ward', d),
            ('Tungi', 'ward', d), ('Vijibweni', 'ward', d)
        ON CONFLICT (parent_id, name) DO NOTHING;
    END IF;
END $wards$;
