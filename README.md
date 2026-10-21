# BongoFlashbangs 0.6.0 — pisk po pełnym oślepieniu

[Pobierz najnowsze wydanie: mod JAR i paczkę zasobów](https://github.com/Silvobi/bongo-flashbangs/releases/latest).

Minecraft Java **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**, Java **25**.
Mod jest wyłącznie serwerowy. Gracz potrzebuje paczki zasobów, ale nie moda klienta.
Mod wykorzystuje model i teksturę dostarczone przez Bongo w `flashbang.bbmodel`.
Zachowano osiem elementów, ich obroty w wielu osiach, inflate, UV oraz wszystkie
dziewięć ustawień wyświetlania: obie dłonie w obu perspektywach, ziemię, GUI,
głowę, item frame i półkę. Osadzona tekstura 32×32 jest kopiowana bez zmian.
Oryginalny plik projektu znajduje się w `artwork/flashbang.bbmodel`.
Mod wykorzystuje natywną fizykę sulfur cube zamiast lotu śnieżki.
Ma rzucany granat, odbicia od ścian i podłoża bez obrażeń,
zapalnik 32 ticki (1,6 s przy 20 TPS), model przedmiotu oraz dźwięki rzutu/odbicia/wybuchu.
Błysk wybiera poziom według odległości oraz kierunku patrzenia w momencie wybuchu.
Rzucający też może zostać oślepiony. Ściany zasłaniające linię od granatu do oczu
blokują efekt. Martwi gracze i spectatorzy są pomijani.

Crafting jest bezkształtny: **1 proch + 1 pył jasnogłazu + 1 sztabka żelaza → 2 flashbangi**.
Składniki można rozmieścić dowolnie w siatce 2×2 ekwipunku lub 3×3 stołu rzemieślniczego.
Receptura pojawia się w książce po zdobyciu dowolnego ze składników. Wynik ma ten sam
model, oznaczenie, nazwę i cooldown co granat z komendy. Receptura jest częścią JAR-a moda.
Flashbang pozostaje śnieżką, ale jego oznaczenie wyklucza go z wabienia/karmienia
dorosłego happy ghasta i ghastlinga. Nie może być składnikiem innej receptury:
zabezpieczono dopasowanie składników, liczenie przedmiotów w książce receptur
oraz wyszukiwanie receptur, również ścieżki z pamięcią używane przez automat crafter.
Zwykła śnieżka nadal wabi/karmi happy ghasta i nadaje się do standardowych receptur.

Opór powietrza wynosi **0**. Zwykły rzut ma prędkość **1,35 bloku/tick**,
a rzut na kucaku **0,2285625 bloku/tick**. Dla nieruchomego kucającego gracza,
patrzącego 85° w górę (poprawka rzutu wynosi dodatkowe 5°), zmierzony na serwerze pułap
to **2,25 m od stóp do środka granatu**, po 8 tickach (0,4 s przy 20 TPS).
To rzut bez skoku i bez odbicia od przeszkody;
odziedziczony pionowy ruch gracza może podnieść lub obniżyć ten pułap.
Odstęp pomiędzy rzutami to **30 ticków (1,5 s przy 20 TPS)**.

Model ustawia swoją długą oś w kierunku lotu i delikatnie obraca się poprzecznie.
Prędkość obrotu rośnie wraz z prędkością lotu. Kierunek łagodnie zmienia się przy
odbiciu, a transformacja modelu jest interpolowana przez vanilla item display.
Punkt obrotu pozostaje w środku granatu. Skala lotu uwzględnia wszystkie orientacje,
aby obracający się model mieścił się w korpusie kolizji; displaye autora dla dłoni,
GUI, ziemi, item frame itd. są zachowane w paczce.

Rzut dziedziczy ruch gracza w poziomie oraz prędkość Y w powietrzu: podczas wznoszenia
granat dostaje dodatkowy pęd w górę, a podczas spadania w dół. Na ziemi pionowy pęd gracza
jest pomijany. Używamy `getKnownMovement()`, tak jak vanilla pociski — u gracza serwerowego
zwykłe `getDeltaMovement()` może różnić się od ruchu zgłoszonego przez klienta.

Przy **pełnym** oślepieniu serwer jednorazowo zatrzymuje wszystkie grające dźwięki
u trafionego gracza. Przez pierwsze **40 ticków (2 s przy 20 TPS)** odrzuca dla niego
nowe pakiety `ClientboundSoundPacket` i `ClientboundSoundEntityPacket`, także w paczkach
zbiorczych. Dźwięk wraca wraz z początkiem zanikania bieli; odrzucone dźwięki nie są
kolejkowane ani później odtwarzane. Pozostali gracze słyszą normalnie.
Kolejny pełny błysk rozpoczyna okres wyciszenia od nowa. Słaby/średni efekt nie wycisza;
`clear`, śmierć, zmiana wymiaru i rozłączenie kończą wyciszenie.

Pełny błysk uruchamia od razu także `bongoflashbangs:earring`, wyłącznie u trafionego
gracza. Nagranie Bongo zawiera **2 s ciszy + 3 s oryginalnego pisku**, razem 5 s:
pisk zaczyna się przy zejściu pełnej bieli i wybrzmiewa jeszcze około 2 s po
odzyskaniu obrazu. Nie skracamy oryginalnego SFX do jednej sekundy.
Plik jest Ogg Vorbis, 48 kHz, stereo, z zachowaną długością i bez zmiany tempa/tonu.
Oryginał znajduje się w `artwork/earring-original.ogg`.

Pakiet dźwięku wysyłamy po jednorazowym stop-all. Filtr wyciszenia przepuszcza dokładnie
ten identyfikator, również w pakietach zbiorczych. Dźwięk jest przypięty do gracza,
używa kategorii MASTER i nie jest rozgłaszany innym graczom. Kolejny pełny błysk
zatrzymuje poprzednie nagranie i uruchamia nowe od ciszy. `clear` oraz jawna zamiana
na słabszy efekt testowy zatrzymują pisk. Zwykłe usunięcie zakończonego shadera
po 3,5 s pozwala nagraniu wybrzmieć do końca.
Nie ma osobnego zadania serwerowego uruchamiającego pisk po 40 tickach: cisza jest
w samym pliku. Zasób jest wstępnie ładowany. Gdy animacja oparta na czasie gry
zwalnia lub zostaje zresetowana, jej synchronizacja z audio czasu rzeczywistego
może się różnić od nominalnych 2 s.

Nie wysyłamy cyklicznych `stopsound` ani innych pakietów podtrzymujących wyciszenie.
Filtr działa podczas zwykłego wysyłania pakietów. Własne kroki, niszczenie/używanie
przedmiotów oraz inne dźwięki uruchamiane lokalnie przez klienta mogą być słyszalne.
Zdarzenia świata/encji mogą również uruchamiać lokalny dźwięk; nie blokujemy ich
pakietów, aby zachować animacje i aktualizacje gry. Mod serwerowy nie zmienia suwaka
głośności klienta. Zatrzymana muzyka/długie dźwięki nie są wznawiane z poprzedniej pozycji.

## Zasięg według rysunku Bongo

| Położenie wybuchu względem spojrzenia | Pełne | Średnie | Słabe |
| --- | --- | --- | --- |
| Stożek 60° (odchylenie do 30°) | do 15 m | powyżej 15 do 20 m | powyżej 20 do 24 m |
| Między stożkami 60° i 120° (odchylenie ponad 30° do 60°) | do 2 m | powyżej 2 do 6 m | powyżej 6 do 17 m |
| Poza stożkiem 120°, również za plecami | do 1 m | powyżej 1 do 2 m | brak |

Poza tymi strefami nie ma oślepienia. Na granicy wybierana jest mocniejsza strefa.
60° i 120° oznaczają pełną szerokość stożka: odpowiednio ±30° i ±60°.
Liczymy odległość 3D od oczu do środka granatu; 1 blok = 1 metr.
Kierunek uwzględnia zarówno obrót w poziomie, jak i patrzenie w górę/dół.
Rysunek źródłowy znajduje się w `artwork/exposure-diagram.png`.
Progi są w `FlashExposure.java`. Obrót po wybuchu nie zmienia już poziomu błysku.

### Poprawka paczki fix1

Naprawiono błąd potwierdzony w logu klienta: `invalid directive: moj_import`
w `bongoflashbangs:post/clock`. Minecraft 26.3 używa `#include`; poprawiono
dyrektywy w `clock.fsh` i `composite.fsh`. Poprawka dotyczy paczki zasobów;
JAR 0.1.0 pozostaje zgodny. Po błędzie gra mogła odznaczyć paczki: należy
ponownie włączyć poprawiony ZIP. Test kompilacji używa teraz niezmienionych
plików shaderów i callbacków importów ShaderC, a dodatkowy test potwierdza,
że stara dyrektywa jest odrzucana. Poprzedni test podstawiał treść importów
przed kompilacją, przez co nie wykrywał tego błędu.

## Instalacja i test

1. Zastąp stary JAR przez `BongoFlashbangs-0.6.0.jar` w `mods/` serwera Fabric
   z Fabric API. W folderze ma zostać jedna wersja BongoFlashbangs.
2. Gracz zastępuje starą paczkę przez `BongoFlashbangs-resourcepack-26.3-0.6.0.zip`.
   Nowa paczka zawiera SFX; starsze paczki nie mają tego dźwięku.
   Docelowo serwer może rozsyłać ten sam ZIP przez `resource-pack` w `server.properties`.
   ZIP nie jest automatycznie hostowany ani wysyłany przez ten prototyp.
3. Uruchom ponownie serwer. Jako operator z uprawnieniami poziomu 2 wpisz:

```mcfunction
/bongoflashbangs test
/bongoflashbangs test @a
/bongoflashbangs test weak
/bongoflashbangs test medium
/bongoflashbangs test full
/bongoflashbangs test weak @a
/bongoflashbangs test medium @a
/bongoflashbangs test full @a
/bongoflashbangs clear
/bongoflashbangs clear @a
/bongoflashbangs give
/bongoflashbangs give @a
```

`give` wręcza 4 flashbangi. Prawy przycisk myszy rzuca granat; kucanie podczas
rzutu daje słabszy rzut. Odstęp między rzutami to 30 ticków. Przedmiot jest oznaczoną
śnieżką z własnym modelem. Zmiana samej nazwy zwykłej śnieżki nie zamienia jej w granat.
W locie fizycznym korpusem jest sulfur cube w skali 0,35, z atrybutami:
`bounciness=0.58`, `gravity=0.03`, `air_drag_modifier=0` i `friction_modifier=1`.
Zwykły mob porusza się po stronie autorytatywnej i używa interpolacji mobów;
klient nie wykonuje już przewidywania śnieżki. Kolizje obsługuje Minecraft dla całego
hitboxu. Korpus jest niewidzialny, a model granatu to vanilla `item_display`
jadący jako jego pasażer. Przesunięcie i skala modelu wynikają z pełnych granic
wyeksportowanej geometrii, łącznie z obrotami i inflate; model mieści się w 90%
fizycznego korpusu. Obliczenia uwzględniają też obrót Y wykonywany przez renderer
item display w vanilla. W locie używamy kontekstu `NONE`: ustawienie `fixed`
autora pozostaje przeznaczone dla item frame i nie przesuwa lecącego granatu.
Oba typy encji istnieją na zwykłym kliencie. Nie rejestrujemy nowego typu encji.

Usunięto cele zachowania moba, zbieranie przedmiotów i interakcje z kubełkiem/nożycami.
Nie używamy `NoAI`: w tej wersji gry zatrzymywało także ruch sulfur cube.
Oznaczona śnieżka wewnątrz slotu BODY włącza zachowanie fizycznej kuli i nie pasuje do
żadnego archetypu bloku, więc korpus nie dostaje eksplozji TNT ani kontaktowych obrażeń.
Własny zapalnik wywołuje wyłącznie efekt flashbanga. Błysk nie niszczy bloków i nie
zadaje obrażeń. Zwykłe sulfur cubes i niezaznaczone śnieżki zachowują standardowe działanie.

Serwer zaznacza punkt ścieżki przez `syncPosition` i wymusza normalną transmisję przez
`needsSync`. To ważne: natywny tracker mobów zużywa pierwszą flagę do zapamiętania
kroku, a sama flaga nie wymusza wysyłania w każdym ticku. Aktualizacje ruchu idą jednym
standardowym mechanizmem Minecrafta, z precyzyjną pozycją i natywną ścieżką interpolacji,
bez dodatkowego strumienia ręcznych teleportów. Nie jest to pełna symulacja obracającej
się bryły ani własna fizyka klienta.

Zapalnik biegnie według czasu świata. Grenady są dodatkowo sprawdzane w ticku serwera,
aby zapalnik nie zatrzymał się w załadowanym chunku poza zasięgiem symulacji.
Granat nie wymusza ładowania nowych chunków, nie przechodzi przez portale i nie jest
zapisywany na dysk jako zwykły mob; po rozładowaniu chunka znika.
Po zakończeniu zapalnika lub usunięciu korpusu usuwany jest też model.

Po błysku odwróć kamerę: zapamiętana scena zostanie na swoim miejscu na ekranie,
a bieżący świat będzie stopniowo wracał pod nią. Po zakończeniu danego efektu serwer
usuwa go z zapasem 10 ticków (0,5 s przy 20 TPS); w czasie zapasu shader jest już
przezroczysty. Ponowienie tego samego lub mocniejszego błysku rozpoczyna nowy efekt.
Słabszy wybuch nie skraca trwającego mocniejszego oślepienia. Komendy testowe zawsze
uruchamiają wybrany poziom, również słabszy. Mod usuwa tylko sześć własnych wariantów,
zachowując pozostałe efekty gracza. Dotychczasowe `/bongoflashbangs test` daje pełny efekt.

## Sam /posteffect — bez moda

Wystarczy włączona paczka i dostęp do komend vanilla:

```mcfunction
/posteffect add @s bongoflashbangs:flash
/posteffect add @s bongoflashbangs:flash_medium
/posteffect add @s bongoflashbangs:flash_weak
```

Wybierz jeden z powyższych efektów. Ich czasy to odpowiednio 3 s, 1 s i 0,5 s.
Po upływie tego czasu shader przepuszcza obraz bez zmian, lecz nadal jest na liście efektów.
Przed kolejnym testem usuń go, odczekaj chwilę, a następnie dodaj ponownie:

```mcfunction
/posteffect remove @s bongoflashbangs:flash
/posteffect add @s bongoflashbangs:flash
```

Nie wykonuj usunięcia i dodania tego samego ID jednocześnie w jednej funkcji:
klient może zobaczyć tylko końcową listę i zachować poprzednią pamięć shadera.
Komenda moda przełącza podstawowy identyfikator / jego wariant `_alt`, aby
zwykłe ponowienie resetowało obraz i zegar. Każdy z trzech poziomów ma taką parę.
Do ręcznego zatrzymania wystarczy `posteffect remove`; nie trzeba czyścić innych efektów.

## Wygląd błysku

- **Słabe, 0,5 s:** przybielenie o maksymalnej sile 28%, utrzymane przez 25 ms,
  zanikające do 150 ms. Delikatny nieruchomy powidok zanika do 500 ms.
- **Średnie, 1 s:** pełna biel przez 150 ms, ustępująca do 400 ms;
  średni powidok zanika do 1 s.
- **Pełne, 3 s:** pełna biel przez 2 s, następnie jednoczesne zanikanie
  bieli i powidoku przez 1 s. Świat i kamera cały czas działają pod efektem.

To własna interpretacja efektu CS2, bez kopiowania jego zasobów ani deklaracji
identycznych parametrów. Klatka jest przechwytywana po odebraniu efektu przez klienta,
**przed nałożeniem bieli** w pierwszej renderowanej klatce. Nie jest to literalnie
poprzednia wyświetlona klatka sprzed odebrania pakietu: ta wymagałaby stale aktywnego
bufora historii lub dodatkowego kodu klienta. Obraz pochodzi z vanilla `minecraft:main`
w momencie wykonywania efektu; nie wyłącza osobno renderowanego HUD-u.

Parametry znajdują się w `resourcepack/assets/bongoflashbangs/post_effect/`
w parach `flash*.json` / `flash*_alt.json`, w bloku `FlashConfig`.
Zmieniaj oba pliki danej pary jednakowo. `WhiteHold`, `WhiteEnd`, `GhostEnd`
to sekundy; `GhostStrength` to siła powidoku; `BlurPixels` to promień próbkowania;
`WhiteStrength` reguluje maksymalną biel. Przy zmianie długości `GhostEnd`
zmień też czas danego poziomu w `FlashStrength.java` (ticki przy 20 TPS).

Bufor `history` jest trwały między klatkami. Przechwycenie i kopiowanie używają
oddzielnych buforów, aby nigdy nie czytać tekstury, do której trwa zapis.
Znacznik czasu korzysta z trzech kanałów RGBA8 i poprawnie obsługuje zawinięcie GameTime.
Animacja jest związana z czasem gry, a nie liczbą klatek. Zmiana rozmiaru okna resetuje
obraz historii: prototyp pobierze nowy obraz i dokończy istniejące zanikanie.
Przeładowanie zasobów odtwarza pamięć shadera i może zresetować jego animację.

## Budowanie i weryfikacja

Z Javą 25 ustawioną jako `JAVA_HOME`:

```text
gradlew.bat build
gradlew.bat resourcePack
gradlew.bat verifyEffect
gradlew.bat verifyExposure
gradlew.bat verifyModel
gradlew.bat verifySoundResource
```

Na Linux/macOS użyj `./gradlew` zamiast `gradlew.bat`. JAR znajduje się w
`build/libs/`, a ZIP paczki zasobów w `build/distributions/`. Test `verifyEffect`
ma zależności natywne dla Windows; pozostałe polecenia budowania są przenośne.

`verifyEffect` na Windows sprawdza wszystkie sześć JSON-ów prawdziwym kodekiem Minecrafta,
kompiluje niezmienione pliki wszystkich czterech shaderów przez ShaderC z obsługą
`#include` dla obu konwencji głębi oraz renderuje testy na ukrytym
kontekście OpenGL. Czyta parametry bezpośrednio z JSON-ów paczki. Sprawdza częściowe
przybielenie słabego błysku, pełną biel dwóch mocniejszych, utrzymanie bieli przez
2 s w pełnym efekcie, powidok mimo zmiany bieżącej sceny, odzyskanie obrazu po
0,5 / 1 / 3 s, nowy bufor przy ponowieniu oraz zawinięcie zegara.
Test GPU używa małych buforów z kontrolowanymi
kolorami: nie zastępuje oceny wyglądu w grze. Nie przeprowadzono jeszcze testu
wizualnego w pełnym kliencie Minecraft ani testu renderowania Vulkan.

`verifyExposure` sprawdza odległości, obie krawędzie stożków, granice stref,
wybuchy za plecami, kierunek w pionie oraz dystans 3D.
`verifyModel` porównuje model i wszystkie displaye z oryginalnym plikiem Blockbench.

Weryfikacja na osobnym, rzeczywistym serwerze Fabric 26.3 obejmuje:
odbicie od ściany i podłogi, półpłyta, trafienie pod kątem, szybki granat przeciw
cienkiej ścianie, brak przenikania całego hitboxu w kolejnych tickach, zwykła śnieżka
jako kontrola, skalowanie, typ sulfur cube, przypięcie modelu, zapalnik i usunięcie
obu encji. Osobna replika z prawdziwym `ServerEntity` przechwytuje natywne pakiety
ruchu: test sprawdza pojedynczą aktualizację w tickach przemieszczania i próbuje
osiem punktów pomiędzy wysyłanymi pozycjami, sprawdzając kolizje całego korpusu.
Dodatkowy test korzysta z rzeczywistego `ServerPlayer`, zastępując jedynie transport
sieciowy. Sprawdza wszystkie komendy poziomów i selektory, przełączanie identyfikatorów,
zachowanie obcych efektów, wybór siły przy detonacji z przodu i z boku, zasłonięcie
kamienną ścianą, odwrócenie się oraz słabszy błysk podczas pełnego oślepienia.
Test craftingu sprawdza recepturę załadowaną z JAR-a i wszystkie 528 rozmieszczeń
trzech składników w siatkach 2×2 i 3×3, wynik dwóch poprawnie oznaczonych flashbangów,
brakujące/niewłaściwe/dodatkowe składniki oraz załadowanie danych odblokowania książki.
Test rzutu używa rzeczywistego `ServerPlayer` z zerowym zwykłym delta i niezerowym
ruchem klienta: sprawdza wznoszenie, opadanie, rzut z ziemi oraz dodanie Y do rzutu pod kątem.
Test audio korzysta z prawdziwego `Connection`, załadowanego mixina i kanału Netty:
sprawdza jeden początkowy stop-all bez ponawiania co tick, blokowanie obu rodzajów
pakietów dźwięku, zachowanie dźwięku u innego gracza, pakiety zbiorcze, callbacki,
przepuszczanie pozostałych pakietów, granicę 39/40 ticków, brak późniejszego odtwarzania,
ponowny pełny błysk, `clear`, słabe/średnie efekty i śmierć. Nie przeprowadzono odsłuchu
na pełnym kliencie; test sprawdza zachowanie pakietów, a nie dźwięki generowane lokalnie.
Test audio sprawdza też jeden SFX przy pełnym błysku, kolejność stop-all → SFX,
adresowanie wyłącznie do trafionego gracza, whitelistę konkretnego ID w obu typach
pakietów i w bundle, restart nagrania przy ponowieniu oraz zatrzymanie przez `clear`.
`verifySoundResource` parsuje `sounds.json` prawdziwym parserem Minecrafta i sprawdza
ID, wstępne ładowanie oraz ścieżkę do pliku Ogg. Osobny odczyt PCM potwierdza
dodanie 96000 próbek ciszy na kanał (2 s przy 48 kHz), łączną długość 5 s i
zgodność sygnału końcowej części z oryginałem po ponownym kodowaniu Vorbis.
Dodatkowy test fizyki mierzy rzeczywisty pułap środka granatu od stóp gracza
przy pionowym słabym rzucie z kucania, brak utraty poziomej prędkości w powietrzu,
zależność obrotu od prędkości, kierunek modelu, stały środek obrotu i dopasowanie
obracającej się geometrii do hitboxu. Sprawdza też predykat wabienia i karmienia
dorosłego/baby happy ghasta, zwykłą śnieżkę jako kontrolę, recepturę bloku śniegu
z flashbangiem i bez, książkę receptur, wyszukiwanie z pamięcią i cooldown 29/30 ticków
w prawdziwym handlerze rzutu.
Do powtórzenia testu zbuduj `gradlew.bat verificationJar` i umieść
`build/libs/flashbangs-verification.jar` wraz z modem oraz Fabric API na oddzielnym,
jednorazowym serwerze testowym. Ten dodatek tworzy arenę i sam zamyka serwer po
sprawdzeniu: służy wyłącznie do pustego świata testowego, nie do serwera gry.
Sukces wypisuje `BONGO_PHYSICS_SAFETY_PASS`, `BONGO_SOUND_PASS`, `BONGO_CRAFT_THROW_PASS`, `BONGO_FLASH_SERVER_PASS` oraz `BONGO_PROJECTILE_PASS`,
a błąd `BONGO_PROJECTILE_FAIL`.
To testy fizyki i pakietów; nie zastępują oceny płynności na pełnym kliencie przy
rzeczywistym pingu ani wizualnego sprawdzenia pozycji przypiętego modelu w grze.

Dokumentacja vanilla: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3
Natywne odbicia i atrybuty: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
