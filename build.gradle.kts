tasks.register("gitClone") {
    doLast {
        exec {
            commandLine("git", "clone", "https://github.com/coolguyshivam/MG-inventory.git", "repo")
        }
        exec {
            workingDir("repo")
            commandLine("git", "checkout", "10df98b559867aa1b3be458f1383ab159e3f35ff")
        }
        exec {
            commandLine("sh", "-c", "cp -a repo/. . && rm -rf repo")
        }
    }
}
