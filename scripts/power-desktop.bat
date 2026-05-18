@echo off
:: ============================================================
:: Paramètres d'alimentation — PC de bureau (usage normal)
:: Exécuter en tant qu'Administrateur
:: ============================================================

echo Application des parametres de veille pour PC de bureau...

:: Extinction de l'écran après 15 min (secteur)
powercfg /change monitor-timeout-ac 15

:: Veille après 30 min (secteur)
powercfg /change standby-timeout-ac 30

:: Veille prolongée désactivée
powercfg /change hibernate-timeout-ac 0

echo.
echo Parametres appliques :
echo   - Extinction ecran : 15 min
echo   - Veille           : 30 min
echo   - Veille prolongee : jamais
echo.
pause
