# Privates Git-Remote (einmalig)

Im Verzeichnis `Mycelium/` (eigenes Repo):

```bash
git branch -M main
git remote add origin git@github.com:DEIN_ORG/mycelium.git   # SSH-URL anpassen
git push -u origin main
```

Auf GitHub/GitLab: **neues privates Repository** anlegen (ohne README/License, damit kein Konflikt), dann `remote add` + `push`.

Dieses Repo ist absichtlich **nur** unter `Mycelium/` initialisiert (nicht der gesamte the maintainer-Workspace-Ordner).
