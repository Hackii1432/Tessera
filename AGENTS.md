# Tessera-Xperimental workspace

This checkout is for the owner's Tessera-X experiments. Work exclusively on
`ver/tessera-Xperimental`. The human user has authorized pushing this branch
to `https://github.com/Hackii1432/Tessera.git`.

- Never push another branch, tag, deletion or ref. Use the explicit refspec
  `refs/heads/ver/tessera-Xperimental:refs/heads/ver/tessera-Xperimental`.
- Preserve other contributors' commits; do not force-push.
- Prefer `scripts/push-tessera-xperimental.ps1`, which checks the branch,
  remote, working tree and fast-forward relationship before pushing.
- The local `.githooks/pre-push` enforces the allowed repository and ref.
- `smp.mosaikdev.com` is an active production server. Do not change it or
  connect test clients; builds and workload tests belong in isolated local labs.
- Preserve native reference artifacts and label experimental measurements.
- Follow `docs/SINOPIA-WORKFLOW.md`: export generated Minecraft edits as
  durable feature patches before reapplying the patch stack. Generated sources
  and local worlds/build caches do not belong in the versioned source checkout.
