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

- Le nom de l'item est celui de la colonne **Nom** de l'onglet Suivi (« Rob 01 »).
- Les majuscules, espaces et `_` ne comptent pas : `rob01_photo.jpg` convient aussi.
- Le plot plan peut aussi s'appeler `<item>_plot` ou `<item>_plotplan`.
- Formats d'image acceptés : jpg, png, webp.

## 2. Utilisation

1. **Importer** : au premier lancement, choisir le dossier `PI_Robinetterie` (une seule fois),
   puis le fichier Excel. Les photos et plots plans sont associés automatiquement à chaque item.
2. Toucher un **item** pour ouvrir sa fiche récap (même organisation que l'onglet Fiche) :
   - toutes les cases sont modifiables : toucher la case, puis choisir dans la liste de l'onglet
     DATA ou saisir librement ;
   - **Photo** : « Prendre la photo » (appareil photo de la tablette) ou choisir un fichier ;
     **Plot plan** : « Remplacer le plot plan » ;
   - **Brides** : ajout, suppression, modification. Qté / Diamètre / Longueur des tiges sont
     calculés d'après l'**ABBAQUE** de l'onglet DATA dès que DN ou PN change (longueur RF + la
     longueur de rondelle réglable si Rondelle = O). Toutes les valeurs restent modifiables ;
   - **Commentaire** : texte libre.
3. **✔ VALIDER** : saisir l'objet de la révision (texte libre, « Initial » pour la Rév. 0).
   La révision est figée et son **PDF est généré** dans `Export/PDF`.
4. Une modification après validation ouvre la **révision suivante** (Rév. 1, 2…) : elle repart des
   données de la révision précédente. Les **différences avec la révision précédente sont en
   jaune** à l'écran, dans le PDF et dans l'Excel. Le PDF de la révision précédente est conservé.
5. **Exporter Excel** : crée une copie mise à jour du fichier importé dans `Export/Excel`
   (le fichier d'origine n'est jamais modifié ; macros, mise en forme et autres onglets conservés) :
   - **Suivi** : une ligne par item. La colonne **Rév** porte le numéro de la dernière
     révision : les lignes ne sont pas dupliquées. Les colonnes N° Ligne, N° Opergraph et
     Besoin potence sont ajoutées si elles sont renseignées ;
   - **Matos** : une ligne par bride ; les brides ajoutées ou supprimées sur la tablette sont
     reportées ;
   - **Instruction** : Client / Lieu / Unité / Année, s'ils ont été modifiés ;
   - seules les **révisions validées** sont exportées (un avertissement liste les items modifiés
     mais pas encore validés).

**Réglages** : longueur de rondelle (par défaut, la valeur de l'onglet DATA : 10 mm) et changement
de dossier de travail.

### Rendu PDF (A4 paysage, onglet « Fiche »)

- En-tête : logo ADF, « Client - Lieu / Unité Année », nom de l'item, logo client.
- Tableau des révisions : les 4 dernières.
- Donnée technique, Besoins, Travaux.
- À droite : **photo sur les 3/4 haut**, **localisation sur plot plan sur le 1/4 bas**.
- Tableau des brides : DN, PN, Qté TF, Matière Jt, Obturation, Serrage, Lg TF, Diam TF,
  Matière TF, Rondelle, Neuf TF, et Commentaire. Au-delà de 5 brides, l'item continue sur une
  2ᵉ page.
- **Numéro de page en bas à droite** (« 1/1 » s'il n'y a qu'une page).

## 3. Compiler l'APK (depuis GitHub, lancement manuel)

1. Onglet **Actions** du dépôt → workflow **Build APK** → **Run workflow**.
2. Attendre la fin (~5-10 min), puis télécharger l'APK :
   - soit dans les **Artifacts** du run ;
   - soit à l'adresse fixe
     `https://github.com/dabom1993-pixel/Pr-paration-Int-gr-e/releases/download/tablette-latest/PIRobinetterie.apk`.
3. Copier l'APK sur la tablette et l'installer (autoriser les « sources inconnues »).

La clé de signature est générée une seule fois puis réutilisée : chaque nouvelle version
s'installe par-dessus la précédente **sans perte de données**. Une fois l'application installée,
toucher le **logo ADF** (connexion internet nécessaire) recherche et installe la dernière
version compilée.

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
