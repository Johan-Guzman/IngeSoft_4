module Demo {
    struct Response {
        string value;
        int responseTime;
    };

    interface Printer {
        Response printString(string line);
    };
}
