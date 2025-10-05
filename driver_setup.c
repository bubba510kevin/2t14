#include <windows.h>
#include <winreg.h>

void SetBootStartDriver(const char* driverName)
{
    HKEY hKey;
    char path[256];
    snprintf(path, sizeof(path), "SYSTEM\\CurrentControlSet\\Services\\%s", driverName);

    if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, path, 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS)
    {
        DWORD startType = 0; // boot start
        RegSetValueEx(hKey, "Start", 0, REG_DWORD, (BYTE*)&startType, sizeof(startType));
        RegCloseKey(hKey);
    }
}
