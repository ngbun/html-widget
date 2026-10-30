HTML Widget – Android Studio projekt

Hvad projektet gør
- App-widget til Android-startskærmen.
- Viser en valgt HTML-fil eller en hjemmeside som et statisk billede.
- Har en enkel blokering af nogle kendte annonce-/trackingdomæner.
- Planlægger opdatering med WorkManager hvert 15. minut.

Vigtigt
- Dette er et Android Studio-KILDEPROJEKT, ikke en færdig APK.
- Jeg har ikke kunnet bygge eller teste APK'en på en fysisk Android-enhed her.
- Widgetten viser et øjebliksbillede. Knapper, links og animationer kan ikke bruges direkte i widgetten.
- Android/producenten kan forsinke baggrundsopdateringer. 15 minutter er et ønsket interval, ikke en garanti.
- Annonceblokeringen er enkel domæneblokering, ikke en komplet AdBlock/Firefox-liste. Den fjerner ikke reklamer, der er indlejret direkte i sidens HTML eller leveres fra samme domæne.
- Nogle websites forhindrer indlejret visning eller kræver login/cookies og vil derfor muligvis ikke fungere.

Byg APK
1. Installer Android Studio på en computer.
2. Åbn mappen HtmlWidget som projekt.
3. Lad Android Studio hente Gradle-afhængigheder.
4. Vælg Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Installer app-debug.apk på Android-telefonen.
6. Åbn HTML Widget, indsæt en URL eller vælg en HTML-fil, og tryk Gem.
7. Tilføj widgetten fra telefonens widgetvælger.

Projektet er et første teknisk udgangspunkt og bør bygges/testes og eventuelt tilpasses telefonmodellen, før daglig brug.
