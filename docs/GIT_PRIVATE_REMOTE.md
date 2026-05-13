# Mycelium als eigenständiges privates Repo

**Mycelium ist kein Unterordner eines Monorepos.** Es hat genau **ein** Git-Repository:
das Verzeichnis, in dem dieses `README.md` liegt (Root = `Mycelium/`). Kein
übergeordnetes Git soll alle anderen the maintainer-Projekte mit einschließen.

Empfohlene Ablage auf der Platte: **nebeneinander** mit euren anderen Repos, z. B.:

```text
~/devel/
  Mycelium/          ← nur dieses Repo (.git hier)
  other-project/      ← eigenes Repo
  other-project/      ← eigenes Repo
  …
```

Liegt der Ordner aus historischen Gründen noch unter `…/the maintainer/Mycelium/`,
ist das nur ein **Pfad auf der Festplatte** — solange **kein** Git im
übergeordneten `the maintainer/`-Ordner dieses Projekt trackt, ist das in Ordnung.
Für Klarheit könnt ihr den Ordner später nach außen verschieben:

```bash
mv the maintainer/Mycelium ../Mycelium
cd ../Mycelium
# .git bleibt erhalten; remote + push wie unten
```

---

## Privates Remote (einmalig)

Im **Repo-Root** (dieses Verzeichnis):

```bash
git branch -M main
git remote add origin git@github.com:DEIN_ORG/mycelium.git   # SSH-URL anpassen
git push -u origin main
```

Auf GitHub/GitLab: **neues privates Repository** anlegen (ohne README/License,
damit kein Konflikt), dann `remote add` + `push`.

**Neu von Remote klonen** (auf einem anderen Rechner / sauberer Pfad):

```bash
git clone git@github.com:DEIN_ORG/mycelium.git
cd mycelium
```
