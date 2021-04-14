package de.codesourcery.spritedesigner;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import de.codesourcery.spritedesigner.Sprite.Flip;

public class SpriteSet implements Serializable
{
    public static final long serialVersionUID = 44L;
    
    private final List<Sprite> sprites = new ArrayList<>();
    
    public void add(Sprite g) {
        this.sprites.add( g );
    }
    
    public void add(int idx, Sprite g) {
        this.sprites.add( idx , g );
    }
    
    public void delete(Sprite g) 
    {
        this.sprites.remove(g);
    }

    public List<Sprite> getSprites() {
        return sprites;
    }
    
    public int size() {
        return sprites.size();
    }
    
    public Sprite sprite(int idx) {
        return sprites.get(idx);
    }

    public int indexOf(Sprite sprite) {
        final int idx = sprites.indexOf( sprite );
        if ( idx == -1 ) {
            throw new NoSuchElementException();
        }
        return idx;
    }

    /**
     * Delete all sprites after a certain index.
     * @param start index of first sprite to delete, inclusive
     */
    public void deleteToEnd(int start)
    {
        deleteRange(start, sprites.size()-1 );
    }

    /**
     * Delete range.
     * @param start start index, inclusive
     * @param end end index, inclusive
     */
    public void deleteRange(int start, int end) {
        if ( end < start || start < 0 || end < 0 )
        {
            throw new IllegalArgumentException();
        }
        for ( int count = (end-start+1) ; count > 0 ; count--)
        {
            sprites.remove( start );
        }
    }
    
    public void moveBackwards(Sprite g) {
        int idx = indexOf( g );
        if ( idx > 0 ) {
            swap( g , sprite(idx-1) );
        }
    }
    
    public void moveForwards(Sprite g) 
    {
        int idx = indexOf( g );
        if ( idx+1 < size() ) 
        {
            swap( g , sprite(idx+1) );
        }
    }
    
    public void swap(Sprite a,Sprite b) 
    {
        int idx1 = indexOf(a);
        int idx2 = indexOf(b);
        sprites.set( idx1 , b );
        sprites.set( idx2 , a );
    }
    
    public byte[] getDataRows(boolean onlyMapped) 
    {
        return accumulate( onlyMapped , gl -> gl.getDataRows(Flip.NONE) );
    }
    
    private byte[] accumulate( boolean onlyMapped , Function<Sprite,byte[]> prod) 
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        sprites.stream().filter( in -> onlyMapped ? in.hasIndex() : true ).forEach( sprite -> 
        {
           try 
           {
               out.write( prod.apply( sprite ) );
        } catch (IOException e) {
            e.printStackTrace();
        }
        });
        return out.toByteArray();
    }
    
    public byte[] getDataColumns(boolean onlyMapped) 
    {    
        return accumulate( onlyMapped , gl -> gl.getDataColumns(Flip.NONE) );
    }
    
    public byte[] getDataColumns(boolean onlyMapped,int bitsPerColumn) 
    {    
        return accumulate( onlyMapped , gl -> gl.getDataColumns( bitsPerColumn ) );
    }    
    
    public String getDataRowsAsAssembly(String prefix,Flip flip) 
    {
        return getAsAssembly( prefix , gl -> gl.getDataRows(flip) );
    }
    
    public String getDataColumnsAsAssembly(String prefix,int bitsPerColumn) {
        return getAsAssembly( prefix , gl -> gl.getDataColumns(bitsPerColumn) );
    }
    
    public String getDataColumnsAsAssembly(String prefix,Flip flip) {
        return getAsAssembly( prefix , gl -> gl.getDataColumns(flip) );
    }
    
    private String getAsAssembly(String prefix,Function<Sprite,byte[]> mapper) 
    {
        final List<Sprite> filtered = sprites.stream().filter( Sprite::hasIndex).collect( Collectors.toList() );
        final HexWriter writer = new HexWriter(16,prefix+".db ");
        for ( final Sprite sprite : filtered )
        {
            writer.appendHexString( mapper.apply( sprite ) );
            if ( sprite.index() >= 32 )
            {
                writer.append( " ; '" ).append( (char) sprite.index() ).append( "'" );
            }
            else
            {
                writer.append( " ; not printable" );
            }
            writer.maybeAppendNewline();
        }
        return writer.toString();
    }
    
    public boolean isEmpty() {
        return sprites.isEmpty();
    }
    
    public String getSpriteMappingAsAssembly(String prefix) 
    {
        final List<Sprite> filtered = sprites.stream().filter( Sprite::hasIndex ).collect( Collectors.toList() );
        final boolean allHaveSameSize = allSpritesHaveSameSizeInBytes( Sprite::hasIndex );
        
        final Map<Integer,Integer> asciiToIndex = new HashMap<>();
        for ( int i = 0 ; i < filtered.size() ; i++ ) 
        {
            asciiToIndex.put( filtered.get(i).index() , i );
        }
        
        final HexWriter result = new HexWriter( 16 , prefix + ".db " );
        if ( allHaveSameSize ) 
        {
            result.result.append("; All sprites have the same size,this table maps to the sprite index");
            if ( ! isEmpty() ) {
                result.result.append("(").append( sprites.get(0).getWidth()).append( "x").append(+sprites.get(0).getHeight()).append(" pixels)");
            }
        } else {
            result.result.append("; Sprites have different sizes,this mapping table holds 4-byte entries with each containing 16-bit offset,sprite width (pixels),sprite height (pixels)");
        }
        result.result.append("\n");
        
        int tableByteOffset = 0;
        for ( int i = 0 ; i < 256 ; i++ ) 
        {
            final Integer glyphIndex = asciiToIndex.get( i );
            if ( glyphIndex != null ) 
            {
                final Sprite sprite = sprites.get( glyphIndex );
                if ( allHaveSameSize ) {
                    result.appendHexByteString(glyphIndex);
                } 
                else 
                {
                    System.out.println("Got sprite at offset "+tableByteOffset);
                    result.appendHexWordStringLittleEndian( tableByteOffset )
                          .appendHexByteString( sprite.getWidth() )
                          .appendHexByteString( sprite.getHeight() );
                }
                System.out.println("Sprite "+glyphIndex+" has size "+sprite.getSizeInBytes());
                tableByteOffset += sprite.getSizeInBytes();
            } else {
                if ( allHaveSameSize ) { // one byte per entry 
                    result.appendHexByteString( 0 );
                } else {
                    result.appendHexWordStringLittleEndian( 0 ); // 4 bytes per entry (16 bit offset,sprite width,sprite height)
                    result.appendHexWordStringLittleEndian( 0 );
                }
            }
        }
        return result.toString();
    }
    
    private static class HexWriter 
    {
        private static final String HEX_CHARS = "0123456789abcdef";
        
        protected final StringBuilder result = new StringBuilder();

        private final String linePrefix;
        private final int bytesPerRow;
        
        private int currentByteCount=0;
        private boolean printLinefeed;
        
        public HexWriter(int bytesPerRow) 
        {
            this.bytesPerRow = bytesPerRow;
            this.linePrefix = null;
        }
        
        public String toString() {
            return result.toString();
        }
        
        public HexWriter(int bytesPerRow,String linePrefix) 
        {
            this.bytesPerRow = bytesPerRow;
            this.linePrefix = linePrefix;
        }
        
        public HexWriter append(String s) {
            result.append( s );
            return this;
        }
        
        public HexWriter append(char s) {
            result.append( s );
            return this;
        }
        
        private HexWriter writeByte(String s) 
        {
            if ( printLinefeed ) 
            {
                appendNewline();
                currentByteCount = 0;
                printLinefeed = false;
            }
            if ( currentByteCount != 0 ) 
            {
                result.append( "," );
            } 
            else if ( linePrefix != null ) 
            {
                result.append( linePrefix );
            }
            result.append(s);
            currentByteCount++;
            if ( currentByteCount == bytesPerRow ) 
            {
                currentByteCount = 0;
                printLinefeed = true;
            }
            return this;
        }
        
        public HexWriter maybeAppendNewline() {
            printLinefeed = true;
            return this;
        }        
        
        public HexWriter appendNewline() {
            result.append( "\n" );
            return this;
        }
        
        public HexWriter appendHexByteString(int value) 
        {
            return writeByte( "0x"+byteToHex(value) );
        }

        public HexWriter appendHexWordStringLittleEndian(int value) 
        {
            writeByte( "0x"+byteToHex( value & 0xff ) );
            return writeByte( "0x"+byteToHex( (value & 0xff00) >>> 8) );
        }    

        private String byteToHex(int value) 
        {
            if ( value < 0 || value > 255 ) {
                throw new IllegalArgumentException("Value out of byte range: "+value);
            }
            final int lo = value  & 0x0f;
            final int hi = (value & 0xf0)>>>4;
            return Character.toString( HEX_CHARS.charAt( hi ) ) + Character.toString( HEX_CHARS.charAt(lo) );
        }

        public HexWriter appendHexString(byte[] data) 
        {
            for ( byte b : data ) 
            {
                appendHexByteString( b & 0xff );
            }
            return this;
        }    
    }
    
    public Dimension getMinSize(Predicate<Sprite> pred) 
    {
        final Dimension result = new Dimension();
        if ( isEmpty()) {
            return result;
        }
        result.width = Integer.MAX_VALUE;
        result.height = Integer.MAX_VALUE;
        for ( Sprite s : sprites )  
        {
            if ( pred.test( s ) ) 
            {
                result.width = Math.min( result.width , s.getWidth() );
                result.height = Math.min( result.height , s.getHeight() );
            }
        }
        return result;
    }
    
    public Dimension getMaxSize(Predicate<Sprite> pred) 
    {
        final Dimension result = new Dimension();
        if ( isEmpty()) {
            return result;
        }
        result.width = Integer.MIN_VALUE;
        result.height = Integer.MIN_VALUE;
        for ( Sprite s : sprites )  
        {
            if ( pred.test( s ) ) 
            {
                result.width = Math.max( result.width , s.getWidth() );
                result.height = Math.max( result.height , s.getHeight() );
            }
        }
        return result;
    }
    
    public boolean allSpritesHaveSameSizeInPixels() 
    {
        boolean result = true;
        if ( ! sprites.isEmpty() ) 
        {
            final Dimension size = sprites.get(0).size;
            return sprites.stream().allMatch ( s -> s.size.equals( size ) );
        }
        return result; 
    }
    
    public boolean allSpritesHaveSameSizeInBytes(Predicate<Sprite> pred) 
    {
        boolean result = true;
        if ( ! sprites.isEmpty() ) 
        {
            final int size = sprites.get(0).getSizeInBytes();
            return sprites.stream().filter(pred).allMatch ( s -> s.getSizeInBytes() == size );
        }
        return result; 
    }    
}