# Open-Source Licenses & Attributions

BomboAddons incorporates and builds upon several open-source community projects. This document lists the licenses and attributions for third-party libraries and modules integrated into BomboAddons.

---

## 1. BetterPV (Profile Viewer)
* **Package:** `dev.vy.betterpv`
* **Original Project:** Better Skyblock Profile Viewer (BetterPV)
* **License:** [Mozilla Public License Version 2.0 (MPL-2.0)](https://www.mozilla.org/en-US/MPL/2.0/)
* **Compliance Notes:**
  * Incorporated under MPL-2.0 Section 3.3 (*Distribution of a Larger Work*).
  * In accordance with Section 3.1 and 3.2, all source code for covered software and modifications is publicly available in this repository.
  * All third-party proprietary API endpoints (`api.vyriv.dev`) have been decoupled; network requests are routed via BomboAddons's own API services (`api.bombo.dpdns.org`) or official Hypixel endpoints.

---

## 2. Skyblocker
* **Components:** Egg Finder / Hoppity WebSocket (`me.bombo.bomboaddons.eggfinder`), Storage Overlay logic
* **Original Project:** Skyblocker
* **License:** [GNU Lesser General Public License Version 3 (LGPL-3.0)](https://www.gnu.org/licenses/lgpl-3.0.en.html)
* **Compliance Notes:**
  * Incorporated under LGPL-3.0 Section 4 (*Combined Works*).
  * Source code is available in this repository.
  * Connects to public community infrastructure (`wss://ws.hysky.de`) with local fallbacks.

---

## 3. NotEnoughUpdates (NEU) & NEU-Repo
* **Components:** `NEUDownloader`, `NeuRepoCache`, `SkyBlockPackCache`
* **Original Project:** [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates) / [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
* **License:** [GNU Lesser General Public License Version 3 (LGPL-3.0)](https://www.gnu.org/licenses/lgpl-3.0.en.html) / Creative Commons

---

## 4. SkyHelper, SkyCofl, Athen & Community Pricing
* **Components:** Item price estimations and auction history
* **Services:**
  * SkyHelperBot Prices: [SkyHelperBot/Prices](https://github.com/SkyHelperBot/Prices)
  * SkyCofl Auction Database: [sky.coflnet.com](https://sky.coflnet.com/)
  * Athen Skyblock Prices: [athen.aerii.xyz](https://athen.aerii.xyz/)
  * Hypixel Official API: [api.hypixel.net](https://api.hypixel.net/)

---

## 5. Summary of Rights & Fair Use
All covered software is distributed in full compliance with their respective open-source licenses (MPL-2.0, LGPL-3.0, and MIT). No private third-party infrastructure or services are utilized without authorization.
