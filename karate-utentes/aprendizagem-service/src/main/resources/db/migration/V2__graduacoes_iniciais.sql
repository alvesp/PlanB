-- Graduações de exemplo (escala kyu/dan habitual). As cores das faixas e os mínimos
-- variam entre estilos e escolas: ajuste-os pela API (PUT /api/graduacoes/{codigo}).
INSERT INTO graduacao (codigo, nome, cor_faixa, tipo, ordem, presencas_minimas, meses_minimos) VALUES
    ('10KYU', '10º Kyu', 'Branca',            'KYU',  1,  0,  0),
    ('9KYU',  '9º Kyu',  'Amarela',           'KYU',  2, 24,  3),
    ('8KYU',  '8º Kyu',  'Laranja',           'KYU',  3, 24,  3),
    ('7KYU',  '7º Kyu',  'Verde',             'KYU',  4, 30,  4),
    ('6KYU',  '6º Kyu',  'Azul',              'KYU',  5, 30,  4),
    ('5KYU',  '5º Kyu',  'Roxa',              'KYU',  6, 36,  6),
    ('4KYU',  '4º Kyu',  'Castanha',          'KYU',  7, 36,  6),
    ('3KYU',  '3º Kyu',  'Castanha',          'KYU',  8, 40,  6),
    ('2KYU',  '2º Kyu',  'Castanha',          'KYU',  9, 40,  6),
    ('1KYU',  '1º Kyu',  'Castanha',          'KYU', 10, 48,  8),
    ('1DAN',  '1º Dan',  'Preta',             'DAN', 11, 72, 12),
    ('2DAN',  '2º Dan',  'Preta',             'DAN', 12, 96, 24),
    ('3DAN',  '3º Dan',  'Preta',             'DAN', 13, 120, 36);
