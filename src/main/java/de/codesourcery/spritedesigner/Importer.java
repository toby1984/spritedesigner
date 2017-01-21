package de.codesourcery.spritedesigner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Importer {

    public static final File file = new File("/home/tobi/neon_workspace/j6502/src/main/resources/roms/character.rom");
    
    public SpriteSet load() {
        
        try 
        {
            final byte[] data = Files.readAllBytes( file.toPath() );
            int dataPtr = 0;
            
            final SpriteSet result = new SpriteSet();
            
            for ( int i = 0 ; i < 256 ; i++ ) 
            {
                final Sprite g = new Sprite(8, 8);
                for ( int y = 0 ; y < 8 ; y++ ) 
                {
                    final int byteValue = data[dataPtr++] & 0xff;
                    int readMask = 1<<7;
                    for ( int x = 0 ; x < 8 ; x++ ) 
                    {
                        if ( (byteValue & readMask) != 0 ) 
                        {
                            g.setPixel( x , y );
                        }
                        readMask >>>= 1;
                    }
                }
                result.add( g );
            }
            return result;
        } 
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
