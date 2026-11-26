-- F8.24 · I rapporti della diagnostica dal campo. Una riga per rapporto, col JSON intero (gia' validato campo per campo)
-- e le colonne per raggruppare: niente IP, niente identificativi del telefono o dell'installazione.
CREATE TABLE IF NOT EXISTS rapporti (
  id TEXT PRIMARY KEY,                 -- a caso, per rapporto: serve solo a non contarlo due volte
  kind TEXT NOT NULL CHECK (kind IN ('crash', 'exit', 'frames')),
  hour INTEGER NOT NULL,               -- l'ora intera, in millisecondi
  received_day TEXT NOT NULL,          -- il giorno di arrivo (AAAA-MM-GG, UTC), per la pulizia dopo 90 giorni
  app TEXT NOT NULL,
  version TEXT NOT NULL,
  code INTEGER NOT NULL,
  engine TEXT NOT NULL,
  build TEXT NOT NULL,
  maker TEXT NOT NULL,
  model TEXT NOT NULL,
  sdk INTEGER NOT NULL,
  hz INTEGER NOT NULL,
  signature TEXT,                      -- crash: la firma
  reason TEXT,                         -- exit: il motivo
  page TEXT,                           -- frames: la pagina
  frames INTEGER,
  slow INTEGER,
  hangs INTEGER,
  frozen INTEGER,
  hitch_ms INTEGER,
  duration_ms INTEGER,
  body TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS rapporti_app_kind ON rapporti (app, kind);
CREATE INDEX IF NOT EXISTS rapporti_received ON rapporti (received_day);
