package com.dev.ResQNet;

import java.io.IOException;

public interface disasterService {
    
    void aiAnalyzer(byte[] image_bytes);
    void failedAi() throws IllegalStateException, IOException;
    
}
