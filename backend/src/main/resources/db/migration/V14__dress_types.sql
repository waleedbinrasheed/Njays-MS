-- Dress types: a default price, a code for the five built-in types, and no drawn shape.
-- Existing rows are not changed. The five built-in types are added only when no dress type with
-- that name or code exists yet.

ALTER TABLE garment_type ADD COLUMN code VARCHAR(30);
ALTER TABLE garment_type ADD CONSTRAINT uq_garment_type_code UNIQUE (code);
ALTER TABLE garment_type ADD COLUMN default_price NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE garment_type ADD CONSTRAINT chk_garment_type_price CHECK (default_price >= 0);

-- The shape drove the old drawings, which are replaced by reference pictures. The column stays
-- for existing rows but is optional and no longer read.
ALTER TABLE garment_type ALTER COLUMN shape DROP NOT NULL;

INSERT INTO garment_type (name, code, active, default_price)
SELECT v.name, v.code, TRUE, 0
FROM (VALUES
        ('Shalwar Kameez', 'SHALWAR_KAMEEZ'),
        ('Formal Shirts', 'FORMAL_SHIRT'),
        ('Pants/Trousers', 'PANT_TROUSER'),
        ('Waistcoats', 'WAISTCOAT'),
        ('Coat / Suit', 'COAT_SUIT')
     ) AS v (name, code)
WHERE NOT EXISTS (
    SELECT 1 FROM garment_type g WHERE g.code = v.code OR lower(g.name) = lower(v.name)
);

-- Measurement points for each built-in type, in the order of the measurement sheets.
INSERT INTO garment_measurement_point (garment_type_id, label, sort_order, active)
SELECT t.id, p.label, p.sort_order, TRUE
FROM garment_type t
JOIN (VALUES
        ('SHALWAR_KAMEEZ', 'Kameez Length', 1),
        ('SHALWAR_KAMEEZ', 'Shoulder', 2),
        ('SHALWAR_KAMEEZ', 'Chest', 3),
        ('SHALWAR_KAMEEZ', 'Waist', 4),
        ('SHALWAR_KAMEEZ', 'Hip / Seat', 5),
        ('SHALWAR_KAMEEZ', 'Sleeve Length', 6),
        ('SHALWAR_KAMEEZ', 'Armhole', 7),
        ('SHALWAR_KAMEEZ', 'Bicep', 8),
        ('SHALWAR_KAMEEZ', 'Cuff', 9),
        ('SHALWAR_KAMEEZ', 'Neck', 10),
        ('SHALWAR_KAMEEZ', 'Front Length', 11),
        ('SHALWAR_KAMEEZ', 'Back Length', 12),
        ('SHALWAR_KAMEEZ', 'Shalwar Length', 13),
        ('SHALWAR_KAMEEZ', 'Shalwar Waist', 14),
        ('SHALWAR_KAMEEZ', 'Thigh', 15),
        ('SHALWAR_KAMEEZ', 'Knee', 16),
        ('SHALWAR_KAMEEZ', 'Pancha', 17),
        ('FORMAL_SHIRT', 'Shirt Length', 1),
        ('FORMAL_SHIRT', 'Chest', 2),
        ('FORMAL_SHIRT', 'Waist', 3),
        ('FORMAL_SHIRT', 'Hip', 4),
        ('FORMAL_SHIRT', 'Shoulder', 5),
        ('FORMAL_SHIRT', 'Sleeve Length', 6),
        ('FORMAL_SHIRT', 'Armhole', 7),
        ('FORMAL_SHIRT', 'Bicep', 8),
        ('FORMAL_SHIRT', 'Cuff', 9),
        ('FORMAL_SHIRT', 'Neck', 10),
        ('FORMAL_SHIRT', 'Front Length', 11),
        ('FORMAL_SHIRT', 'Back Length', 12),
        ('PANT_TROUSER', 'Waist', 1),
        ('PANT_TROUSER', 'Hip / Seat', 2),
        ('PANT_TROUSER', 'Length', 3),
        ('PANT_TROUSER', 'Inseam', 4),
        ('PANT_TROUSER', 'Rise / Crotch', 5),
        ('PANT_TROUSER', 'Thigh', 6),
        ('PANT_TROUSER', 'Knee', 7),
        ('PANT_TROUSER', 'Bottom', 8),
        ('PANT_TROUSER', 'Calf', 9),
        ('PANT_TROUSER', 'Fly Length', 10),
        ('WAISTCOAT', 'Length', 1),
        ('WAISTCOAT', 'Chest', 2),
        ('WAISTCOAT', 'Waist', 3),
        ('WAISTCOAT', 'Shoulder', 4),
        ('WAISTCOAT', 'Armhole', 5),
        ('WAISTCOAT', 'Neck', 6),
        ('WAISTCOAT', 'Front Width', 7),
        ('WAISTCOAT', 'Back Width', 8),
        ('COAT_SUIT', 'Coat Length', 1),
        ('COAT_SUIT', 'Chest', 2),
        ('COAT_SUIT', 'Waist', 3),
        ('COAT_SUIT', 'Hip', 4),
        ('COAT_SUIT', 'Shoulder', 5),
        ('COAT_SUIT', 'Sleeve Length', 6),
        ('COAT_SUIT', 'Bicep', 7),
        ('COAT_SUIT', 'Cuff', 8),
        ('COAT_SUIT', 'Back Width', 9),
        ('COAT_SUIT', 'Front Length', 10),
        ('COAT_SUIT', 'Neck', 11)
     ) AS p (code, label, sort_order) ON p.code = t.code
WHERE NOT EXISTS (
    SELECT 1 FROM garment_measurement_point m WHERE m.garment_type_id = t.id
);
