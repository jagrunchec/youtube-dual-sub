@echo off
:: ============================================================
:: Paramètres d'alimentation — Mode serveur (toujours allumé)
:: Exécuter en tant qu'Administrateur
:: ============================================================

echo Application des parametres de veille pour mode serveur...

:: Extinction de l'écran après 15 min (économise l'écran)
powercfg /change monitor-timeout-ac 15

:: Veille désactivée
powercfg /change standby-timeout-ac 0

:: Veille prolongée désactivée
powercfg /change hibernate-timeout-ac 0

echo.
echo Parametres appliques :
echo   - Extinction ecran : 15 min
echo   - Veille           : jamais
echo   - Veille prolongee : jamais
echo.
pause
