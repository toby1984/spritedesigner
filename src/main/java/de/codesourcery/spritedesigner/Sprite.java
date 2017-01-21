package de.codesourcery.spritedesigner;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.Serializable;

public class Sprite implements Serializable
{
    public static final long serialVersionUID = 47L;

    public static enum Flip 
    {
        NONE("not flipped"),
        FLIP_X("flipped X") 
        {
            public boolean getPixel(int x,int y, Sprite g) 
            {
                return g.isSet(g.getWidth() - x - 1, y); 
            }            
        },
        FLIP_Y("flipped Y")
        {
            public boolean getPixel(int x,int y, Sprite g) 
            {
                return g.isSet(x,g.getHeight() - y - 1); 
            }            
        },
        FLIP_XY("flipped XY")        
        {
            public boolean getPixel(int x,int y, Sprite g) 
            {
                return g.isSet(g.getWidth() - x - 1 , g.getHeight() - y - 1); 
            }            
        };

        private String name;
        
        private Flip(String name) {
            this.name= name;
        }

        public boolean getPixel(int x,int y, Sprite g) 
        {
            return g.isSet(x, y); 
        }
        
        @Override
        public String toString() {
            return name;
        }
    }

    private boolean[][] data;
    public final Dimension size = new Dimension();
    private boolean indexSet;
    private int index;

    public Sprite(int width,int height) 
    {
        size.setSize( width , height);
        data = newArray(width,height);
    }
    
    private static boolean[][] newArray(int width,int height) 
    {
        boolean[][] data = new boolean[width][];
        for ( int i = 0 ; i < width ; i++ ) 
        {
            data[i] = new boolean[height];
        }
        return data;
    }
    
    public int index() {
        return index;
    }

    public void clear() {
        for ( int x = 0 ; x < getWidth() ; x++ ) {
            for ( int y = 0 ; y < getHeight() ; y++ ) {
                data[x][y]=false;
            }
        }
    }

    public Dimension getSize() {
        return size;
    }
    
    public boolean hasIndex() {
        return indexSet;
    }

    public int getWidth() {
        return size.width;
    }

    public int getHeight() {
        return size.height;
    }    

    public void togglePixel(int x,int y) {
        data[x][y] = ! data[x][y];
    }

    public void setPixel(int x,int y) {
        data[x][y] = true;
    }

    public void clearPixel(int x,int y) {
        data[x][y] = false;
    }

    public boolean isSet(int x,int y) {
        return data[x][y];
    }

    private void assertSize() {
        if ( ( getWidth() % 8 ) != 0 ) {
            throw new IllegalStateException("Glyph width needs to be a multiple of 8");
        }
        if ( ( getHeight() % 8 ) != 0 ) {
            throw new IllegalStateException("Glyph height needs to be a multiple of 8");
        }
    }

    public byte[] getDataRows(Flip flip) 
    {
        assertSize();
        int writePtr = 0;
        final byte[] result = new byte[ sizeInBits() / 8 ];
        for (int y = 0 ; y < getHeight(); y++) 
        {
            int currentByte = 0;
            for ( int x = 0, bitCount = 7 ; x < getWidth() ; x++ ) 
            {
                if ( flip.getPixel(x, y, this) )
                {
                    currentByte |= 1<<bitCount;
                }
                bitCount--;
                if ( bitCount < 0 ) 
                {
                    result[writePtr++] = (byte) currentByte;
                    currentByte = 0;
                    bitCount = 7;
                }
            }
        }
        return result;
    }

    public int sizeInBits() {
        return getWidth() * getHeight();
    }

    public byte[] getDataColumns(int bitsPerColumn) 
    {
        if ( bitsPerColumn < 1 ) {
            throw new IllegalArgumentException("Need at least 1 output bit per column");
        }
        final byte[] result = new byte[ getSizeInBytes() ];

        int writePtr = 0;
        for ( int y=0 ; y < getHeight() ; y+= bitsPerColumn ) 
        {
            for ( int x = 0 ; x < getWidth() ; x++ ) 
            {
                int bitCounter = 0;
                int currentByte = 0;
                for ( ; bitCounter < bitsPerColumn; bitCounter++ ) 
                {
                    currentByte >>>= 1;
                    if ( (y+bitCounter) < getHeight() && isSet( x , y+bitCounter ) ) 
                    {
                        currentByte |= 128;
                    }
                    if ( bitCounter != 0 && (bitCounter%8) == 0 ) 
                    {
                        result[writePtr++] = (byte) currentByte;
                        currentByte = 0;
                    }
                }
                if ( bitCounter != 0 && (bitCounter%8) == 0 ) 
                {
                    result[writePtr++] = (byte) currentByte;
                }
            }
        }
        return result;
    }

    public byte[] getDataColumns(Flip flip) 
    {
        assertSize();
        final byte[] result = new byte[ sizeInBits() / 8 ];
        int writePtr = result.length-1;

        for ( int x = 0 ; x < getWidth() ; x++) 
        {
            int currentByte = 0;
            for ( int y = 0, bitCount = 7 ; y < getHeight() ; y++ ) 
            {
                if ( flip.getPixel(x,y,this) )
                {
                    currentByte |= 1<<bitCount;
                }
                bitCount--;
                if ( bitCount < 0 ) 
                {
                    result[writePtr--] = (byte) currentByte;
                    currentByte = 0;
                    bitCount = 7;
                }
            }
        }
        return result;        
    }

    public boolean hasSize(int w,int h) {
        return getWidth() == w && getHeight() == h;
    }
    
    public void resize(int w, int h,boolean scale) 
    {
        if ( w < 1 || h < 1 ) {
            throw new IllegalArgumentException("Size "+w+"x+"+h+" is too small, needs to be at least 1x1");
        }
        if ( w == getWidth() && h == getHeight() ) {
            return;
        }
        
        final boolean[][] tmp = newArray(w,h);
        
        int actualWidth = 0;
        int actualHeight = 0;
        for ( int x = 0 ; x < getWidth() ; x++ ) {
            for ( int y = 0 ; y < getHeight() ; y++ ) 
            {
                if ( isSet(x,y ) ) {
                    actualWidth = Math.max( actualWidth , x+1 );
                    actualHeight = Math.max( actualHeight , y+1 );
                }
            }
        }
        final boolean downScalingNeeded = actualWidth > w || actualHeight > h;
        final boolean upScalingNeeded = (actualWidth != 0 || actualHeight != 0) && (w > getWidth() || h > getHeight());
        
        if ( scale && ( downScalingNeeded || upScalingNeeded ) ) 
        {
            final BufferedImage src = new BufferedImage( getWidth() , getHeight() , BufferedImage.TYPE_BYTE_BINARY );
            for ( int x = 0 ; x < getWidth() ; x++ ) {
                for ( int y = 0 ; y < getHeight() ; y++ ) 
                {
                    if ( isSet(x,y ) ) 
                    {
                        src.setRGB( x ,y , 0xffffff);
                    } else {
                        src.setRGB( x ,y , 0 );
                    }
                }
            }
            final BufferedImage dst = new BufferedImage( w , h , BufferedImage.TYPE_BYTE_BINARY );
            final Graphics2D dstGfx = dst.createGraphics();
            dstGfx.drawImage(src, 0 , 0 , w , h , 0, 0, getWidth() , getHeight() , null );
            dstGfx.dispose();
            
            final int[] buffer = new int[1];
            for ( int x = 0 ; x <  w ; x++ ) 
            {
                for ( int y = 0 ; y < h ; y++ ) 
                {
                    dst.getData().getPixel(x,y,buffer);
                    tmp[x][y] = buffer[0] != 0; 
                }
            }            
        } 
        else 
        {
            for ( int x = 0 ; x < w && x < getWidth() ; x++ ) 
            {
                for ( int y = 0 ; y < h && y < getHeight() ; y++ ) 
                {
                    tmp[x][y] = this.data[x][y];
                }
            }
        }
        this.data = tmp;
        this.size.setSize(w,h);
    }
    
    public int getSizeInBytes() 
    {
        int wPix = (getWidth()%8) == 0 ? getWidth() : (int) ( Math.ceil( getWidth() / 8.0 )*8 );
        int hPix = (getHeight()%8) == 0 ? getHeight() : (int) ( Math.ceil( getHeight() / 8.0 )*8 );
        return (wPix*hPix)/8;
    }
    
    public void setIndex(int idx) 
    {
        if ( idx < 0 ) {
            throw new IllegalArgumentException("Index needs to be >= 0 , was: "+idx);
        }
        this.index = idx;
        this.indexSet = true;
    }
    
    public void clearIndex() {
        this.index = 0;
        this.indexSet = false;
    }
    
    public void invert() {
        for ( int x = 0 , w = getWidth() ; x < w ; x++ ) 
        {
            for ( int y = 0 , h = getHeight() ; y < h ; y++ ) 
            {
                data[x][y] = ! data[x][y];
            }
        }
    }
    
    public boolean isBlank() 
    {
        for ( int x = 0 ; x < getWidth() ; x++ ) {
            for ( int y = 0 ; y < getHeight() ; y++ ) 
            {
                if ( isSet(x,y ) ) 
                {
                    return false;
                }
            }
        }
        return true;
    }
    
    public void cropToSize() 
    {
        if ( isBlank() ) {
            return;
        }
        
        int minY = Integer.MAX_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for ( int x = 0 ; x < getWidth() ; x++ ) 
        {
            for ( int y = 0 ; y < getHeight() ; y++ ) 
            {
                if ( isSet(x,y ) ) 
                {
                    maxX = Math.max( maxX ,  x );
                    maxY = Math.max( maxY ,  y );
                    minX = Math.min( minX ,  x );
                    minY = Math.min( minY ,  y );
                }
            }
        }
        final int newWidth = (maxX-minX)+1;
        final int newHeight = (maxY-minY)+1;
        if ( hasSize(newWidth,newHeight ) ) 
        {
            return;
        }
        
        final boolean[][] tmp = newArray(newWidth,newHeight);
        for ( int x = 0 ; x < newWidth ; x++ ) 
        {
            for ( int y = 0 ; y < newHeight ; y++ ) 
            {
                tmp[x][y] = isSet( minX+x, minY+y );
            }
        }
        this.data = tmp;
        this.size.setSize( newWidth , newHeight );
    } 
    
    public void setToImage(BufferedImage src) 
    {
        final BufferedImage dst;
        if ( src.getType() != BufferedImage.TYPE_BYTE_BINARY || src.getData().getNumBands() != 1 ) 
        {
            dst = new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_BYTE_BINARY);
            final Graphics2D gfx = dst.createGraphics();
            gfx.drawImage( src , 0 , 0 ,null );
            gfx.dispose();
        } else {
            dst = src;
        }
        final Raster raster = dst.getData();
        resize( dst.getWidth() , dst.getHeight() , false );
        final int[] color = new int[ 1 ];
        for ( int x = 0 , w = dst.getWidth() ; x < w ; x++ ) 
        {
            for ( int y = 0 , h = dst.getHeight() ; y < h ; y++ ) 
            {
                raster.getPixel(x,y,color);
                data[x][y] = color[0] != 0; 
            }
        }
    }
}