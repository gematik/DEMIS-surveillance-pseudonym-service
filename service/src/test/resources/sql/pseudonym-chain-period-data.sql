INSERT INTO chain (chain_id) VALUES ('b2df12e8-46d0-45bc-90b4-cb573c4effa2');
INSERT INTO chain (chain_id) VALUES ('2dcea829-6fce-4c1c-83fa-fb948c393292');

INSERT INTO pseudonym_chain (pseudonym, chain_id) VALUES (E'\\xd23d997b50033086023c61d6b5ffb131c2810478d1cf9ce12f5ccc892ed74124', 'b2df12e8-46d0-45bc-90b4-cb573c4effa2'); -- pseudonym before hash = CL-PSEUDO-1
INSERT INTO pseudonym_chain (pseudonym, chain_id) VALUES (E'\\xd7a6204a241b8faeb8707354b789642d5e3e68fdc88a7dd84641a6896f4c98ee', 'b2df12e8-46d0-45bc-90b4-cb573c4effa2'); -- pseudonym before hash = CL-PSEUDO-2
INSERT INTO pseudonym_chain (pseudonym, chain_id) VALUES (E'\\xe2ba3bd061e1c344347c2ed725c9d88e9ecb461716d7fd2eec938612c5f13d14', '2dcea829-6fce-4c1c-83fa-fb948c393292'); -- pseudonym before hash = OTHER-CHAIN-1
INSERT INTO pseudonym_chain (pseudonym, chain_id) VALUES (E'\\xe80389e63f1e896d2146245ade08575815257a8df4926a53b7ff4d797a9c5586', '2dcea829-6fce-4c1c-83fa-fb948c393292'); -- pseudonym before hash = OTHER-CHAIN-2

INSERT INTO period (period_id, chain_id, min_year, max_year) VALUES ('38c7c638-996a-4e10-a50b-bcdb24bbe6bd', 'b2df12e8-46d0-45bc-90b4-cb573c4effa2', 2023, 2023);