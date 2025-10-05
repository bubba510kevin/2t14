#include <iostream>
#include <filesystem>
#include <string>
#include <list>
#include <algorithm>

namespace fs = std::filesystem;

std::list<std::string> getFileNamesWithoutExt(const std::string& folderPath) {
    std::list<std::string> names;

    for (const auto& entry : fs::directory_iterator(folderPath)) {
        if (entry.is_regular_file()) {
            names.push_back(entry.path().stem().string());
        }
    }

    return names;
}


int main() {
    std::string folderPath = "C:/path/to/your/folder"; // change

    std::list<std::string> files = getFileNamesWithoutExt(folderPath);
    int big = files.size();
    std::string dir = "";
    char x = '/';

    for(int i = 001; i <= big; i++){
        try{
            
            std::string z = dir + x + std::to_string(i);
            std::filesystem::create_directories(z);
            return 1;
        }catch (const fs::filesystem_error& e) {
            return 404;

        }

    }
    
    
}


