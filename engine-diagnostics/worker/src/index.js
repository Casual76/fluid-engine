// F8.24 · Il Worker della diagnostica dal campo. Solo l'esportazione di serie: il resto sta in core.js (vedi la nota là).
import { handlers } from "./core.js";

export default {
  fetch: (request, env, ctx) => handlers.fetch(request, env, ctx),
  scheduled: (event, env, ctx) => handlers.scheduled(event, env, ctx),
};
