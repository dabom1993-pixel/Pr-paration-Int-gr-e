# PI Robinetterie — Préparation intégrée (tablette Android)

Application Android (tablette Samsung, **hors connexion**) pour la préparation intégrée de
robinetterie : on prépare un fichier Excel + un dossier photos, on valide chaque item sur la
tablette, et l'application produit **un PDF par item et par révision** (mise en page de l'onglet
« Fiche ») ainsi que **l'Excel mis à jour**.

Même base technique que PV Jointage : Android 7 minimum (API 24), cible Android 14 (API 34),
Kotlin, compilation par GitHub Actions, mise à jour par le logo ADF.

---

## 1. Préparer le dossier sur la tablette (câble USB)

Créer sur la tablette un dossier, par exemple `Documents/PI_Robinetterie`, avec :

```
PI_Robinetterie/
├── Import/
│   ├── PI_Rob.xlsm              ← le fichier Excel (onglets Matos, Suivi, DATA, Instruction)
│   ├── logo_client.png          ← facultatif : logo du client (case "LOGO CLIENT" de la fiche)
│   └── Photos/
│       ├── Rob 01_photo.jpg     ← photo de l'item      (« <Nom de l'item>_photo »)
│       ├── Rob 01_plan.jpg      ← localisation sur plot plan (« <Nom de l'item>_plan »)
│       ├── Rob 02_photo.jpg
│       └── …
└── Export/                      ← créé et rempli par l'application
    ├── PDF/    Rob 01_Rev0.pdf, Rob 01_Rev1.pdf…  (les anciennes révisions sont conservées)
    └── Excel/  PI_Rob_MAJ_20260925_143000.xlsm
```

- Le nom de l'item est celui de la colonne **Item** de l'onglet Suivi (« Rob 01 »).
- Les majuscules, espaces et `_` ne comptent pas : `rob01_photo.jpg` convient aussi.
- Le plot plan peut aussi s'appeler `<item>_plot` ou `<item>_plotplan`.
- Formats d'image acceptés : jpg, png, webp.

### Plot plan (fichier Excel PlotPlan)

Copier aussi dans `Import/` le fichier **PlotPlan** (ex. `PlotPlan_Projet.xlsm`), puis toucher le
bouton **Plot plan** de l'écran principal (**rouge** = pas encore importé, **vert** = importé).
Pour chaque item dont le nom correspond à la colonne **Équipement / TAG** de l'onglet
Interventions, la tablette affiche le plan de son unité (onglet Plans) avec son **rond ou carré**,
à la **couleur de sa famille** (onglet Paramètres), sans légende ni trait. Position : colonnes
« X plan / Y plan », ou à défaut le « Repère » (cellule de l'onglet du plan), comme les macros.
Cette image remplace alors le fichier `<item>_plan.jpg` du dossier Photos.

Toucher le plot plan dans la fiche l'ouvre **en grand** (zoom / déplacement à deux doigts,
double-tap = vue entière), avec **Fermer** et **Modifier** : choix du plan (s'il y en a plusieurs),
déplacement du point (le faire glisser ou toucher le plan), forme **rond / ovale** ou
**carré / rectangle**, **largeur / hauteur**. La localisation modifiée est propre à l'item (elle
suit ses révisions et n'est pas écrasée par un nouvel import du plot plan) ; « Revenir au point de
l'Excel » l'annule. Le plot plan peut être
importé avant ou après le fichier de préparation.

## 2. Utilisation

1. **Importer** : au premier lancement, choisir le dossier `PI_Robinetterie` (une seule fois),
   puis le fichier Excel. Les photos et plots plans sont associés automatiquement à chaque item.
2. Filtrer la liste si besoin : boutons **Unité** et **Famille** (sélection multiple, rien de
   coché = tout), statut (À valider / En cours / Validés) et recherche. Toucher un **item** pour ouvrir sa fiche récap (même organisation que l'onglet Fiche) :
   - toutes les cases sont modifiables : toucher la case, puis choisir dans la liste de l'onglet
     DATA ou saisir librement ;
   - **Photo** : « Prendre la photo » ouvre la prise de vue intégrée (bouton « Retour » pour
     sortir, puis « Garder » ou « Reprendre »). Le **plot plan** vient de l'import et n'est pas
     remplaçable sur la tablette ;
   - **Dates** (ex. Date Transmission) : saisie par calendrier uniquement, écrite dans l'Excel
     comme une vraie date ;
   - **Brides** : ajout, suppression, modification. Le diamètre et la longueur des tiges sont
     calculés d'après l'**ABBAQUE** de l'onglet DATA dès que DN, PN, Face ou Rondelle change
     (longueur RTJ si Face = RTJ, RF sinon, + la longueur de rondelle réglable si Rondelle = O).
     Toutes les valeurs restent modifiables ;
   - **Commentaire** : texte libre.
3. **✔ VALIDER** : saisir l'objet de la révision (texte libre, « Initial » pour la Rév. 0), puis
   - « **Valider et générer le PDF** » : la révision est figée et son PDF est créé dans `Export/PDF` ;
   - « **Valider sans PDF** » : la révision est figée et sauvegardée, sans PDF. Toucher à nouveau
     « VALIDER » (sans modification) permet de générer son PDF plus tard.
4. Une modification après validation ouvre la **révision suivante** (Rév. 1, 2…) : elle repart des
   données de la révision précédente. Les **différences avec la révision précédente sont en
   jaune** à l'écran, dans le PDF et dans l'Excel. Le PDF de la révision précédente est conservé.
5. **Exporter Excel** : crée une copie mise à jour du fichier importé dans `Export/Excel`
   (le fichier d'origine n'est jamais modifié ; macros, mise en forme et autres onglets conservés) :
   - **Suivi** : une ligne par item. La colonne **Rév** porte le numéro de la dernière
     révision : les lignes ne sont pas dupliquées ;
   - **Matos** : une ligne par bride ; les brides ajoutées ou supprimées sur la tablette sont
     reportées ;
   - **Instruction** : Client / Lieu / Unité / Année, s'ils ont été modifiés ;
   - seules les **révisions validées** sont exportées (un avertissement liste les items modifiés
     mais pas encore validés).

**Réglages** : longueur de rondelle (par défaut, la valeur de l'onglet DATA : 10 mm) et changement
de dossier de travail.

### Rendu PDF (A4 paysage, onglet « Fiche »)

Même grille que l'onglet Fiche (colonnes A à T, lignes 1 à 53) :
- **En-tête** (lignes 1-4) : logo ADF, « Client - Lieu / Unité Année », nom de l'item, logo client.
- **Plot plan** (localisation) : sous le logo, au-dessus du tableau des révisions (A5:D18).
- **Photo** : tout l'espace libre à droite (E5:T45).
- **Révisions** (lignes 19-23) : les 4 dernières.
- **Donnée technique** (Unité / Zone, Chrono ISO, Type, Travaux, Equipement Maitre, N° Ligne,
  N° Opergraph, Classe tuyauterie), puis Hauteur, Poids, besoins échafaudage / calorifuge /
  levage / potence, puis Traçage, Boite à ressort, SOMF, EPI.
- **Brides** (lignes 47-53) : Rep. & Désignation | JOINT (DN, PN, Matière) | BRIDE (Face,
  Obtur, Serrage) | TIGES FILETÉES (Lg, Diam, Matière, Rondelle) | RAAT, et le Commentaire de
  l'item. Au-delà de 5 brides, l'item continue sur une 2ᵉ page.
- **Numéro de page en bas à droite** (« 1/1 » s'il n'y a qu'une page).

Les deux versions du fichier Excel sont acceptées : Suivi avec une colonne « Item » ou « Nom »,
Matos avec « Unité / Zone, Type, Matière, Face, Obtur, Lg, Diam, Matière2, Rondelle, RAAT » ou
les anciens libellés (« Famille, MatièreJ, LgB, DiamB… »).

## 3. Compiler l'APK : version BETA (test) et version finale

Deux applications sont construites à partir du même code :

| | **BETA** (test) | **Finale** (accessible à tous) |
|---|---|---|
| Nom sur la tablette | PI Robinetterie **BETA** (badge orange) | PI Robinetterie |
| Icône | carré **orange** | carré vert |
| Identifiant Android | `com.adf.pirobinetterie.beta` | `com.adf.pirobinetterie` |
| Lien de téléchargement | `…/releases/download/tablette-beta/PIRobinetterie-BETA.apk` | `…/releases/download/tablette-latest/PIRobinetterie.apk` |

Les deux s'installent **côte à côte** sur la même tablette, avec des **données séparées** : tester
la BETA ne touche jamais aux projets de la version finale. Chacune se met à jour par son logo ADF
depuis sa propre release.

Fonctionnement : chaque modification est d'abord publiée en **BETA** ; quand elle est validée sur
la tablette de test, la **finale** est reconstruite depuis le même code.

1. Onglet **Actions** du dépôt → workflow **Build APK** → **Run workflow** → choisir
   **beta** ou **finale**.
2. Attendre la fin (~5-10 min), puis télécharger l'APK (lien ci-dessus, ou **Artifacts** du run).
3. Copier l'APK sur la tablette et l'installer (autoriser les « sources inconnues »).

Le lien BETA n'est pas affiché comme « dernière version » sur GitHub (pré-version), mais le dépôt
étant public, toute personne qui connaît le lien peut la télécharger.

La clé de signature est générée une seule fois puis réutilisée : chaque nouvelle version
s'installe par-dessus la précédente **sans perte de données**.

## 4. Structure du code

```
app/src/main/java/com/adf/pirobinetterie/
├── model/   Item, Bride, Révision, différences (jaune), ABBAQUE, correspondance Fiche ↔ Excel
├── excel/   Lecture (Classeur, ExcelImporter) et réécriture (ExcelExporter) du .xlsm, sans macro
├── pdf/     Mise en page de la fiche (FicheLayout) et génération PDF Android (PdfFiche)
├── data/    Sauvegarde du projet (JSON), dossier de travail (Import/Export), fichiers partagés
├── ui/      Écran principal (liste des items) et écran Fiche
└── update/  Mise à jour par le logo ADF
```

L'application n'utilise aucune bibliothèque externe : uniquement le framework Android et Kotlin.
Les colonnes Excel sont retrouvées par leur **libellé** (ligne d'en-têtes), pas par leur lettre :
ajouter une colonne dans Matos ou Suivi ne casse rien.
