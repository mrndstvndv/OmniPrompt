// IUserService.aidl
package com.mrndstvndv.search;

interface IUserService {
    void destroy() = 16777114;
    boolean setDeveloperSettingsEnabled(boolean enabled) = 1;
    boolean launchSettingsFragment(String fragmentName, String highlightKey) = 2;
}
