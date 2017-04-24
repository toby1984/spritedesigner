package de.codesourcery.spritedesigner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import de.codesourcery.spritedesigner.Sprite.Flip;

public class Main extends JFrame 
{
    private static final File CONFIG_FILE = new File(".chardesigner");

    public static void main(String[] args) throws InvocationTargetException, InterruptedException 
    {
        SwingUtilities.invokeAndWait( () ->  new Main().run() );
    }

    private SpriteSet spriteSet;
    private final EditorPanel editorPanel = new EditorPanel();
    private PreviewPanel previewPanel;

    private File currentFile;

    public Main() 
    {
        super("SpriteDesigner");

        final boolean doImport = false; 
        if ( doImport ) {
            spriteSet = new Importer().load();
        } else {
            spriteSet = new SpriteSet();
            spriteSet.add( new Sprite(24,21) );
        }

        previewPanel = new PreviewPanel(spriteSet);
        editorPanel.setSprite( previewPanel.currentSelection );

        if ( ! doImport ) {
            loadConfig();
        }
    }

    private void run() 
    {
        getContentPane().setLayout( new GridBagLayout() );

        final JScrollPane scrollPane = new JScrollPane( previewPanel );

        final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT , editorPanel , scrollPane );
        splitPane.setDividerLocation( 300 );

        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx=1.0;
        cnstrs.weighty=1.0;
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridheight=1;
        cnstrs.gridwidth=1;
        getContentPane().add( splitPane , cnstrs );
        setPreferredSize( new Dimension(640,480 ) );
        pack();
        setVisible(true);
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        final JMenuBar menuBar = new JMenuBar();
        final JMenu menu = new JMenu("File");
        menuBar.add( menu );

        addMenuItem("Open..." , menu , () -> 
        {
            final SpriteSet set = loadGlyphSet();
            if ( set != null ) {
                setGlyphSet( set );
            }
        });
        
        menu.addSeparator();
        
        addMenuItem("Import image..." , menu , () -> 
        {
            loadFromImage();
            updateWindowTitle();            
        });
        
        addMenuItem("Crop" , menu , () -> 
        {
            currentSelection().cropToSize();
            editorPanel.repaint();
            previewPanel.repaint();
            updateWindowTitle();
        });
        
        addMenuItem("Crop all" , menu , () -> 
        {
            for ( Sprite s : spriteSet.getSprites() ) {
                s.cropToSize();
            }
            editorPanel.repaint();
            previewPanel.repaint();
            updateWindowTitle();
        });        
        
        menu.addSeparator();
        
        addMenuItem("Save" , menu , () -> 
        {
            saveGlyphSet( spriteSet );    
        });   
        addMenuItem("Save as..." , menu , () -> 
        {
            final File file = pickFileToSave();
            if ( file != null ) 
            {
                writeGlyphSet( spriteSet , file );
                currentFile = file;
            }
        });     

        for ( Flip flip : Flip.values() ) 
        {
            addMenuItem("Show as row data ("+flip+")..." , menu , () -> 
            {
                final String asm = "charset:\n; data organization: rows "+flip+"\n"+spriteSet.getDataRowsAsAssembly("    ",flip)+
                        " \ncharset_mapping:\n"+spriteSet.getSpriteMappingAsAssembly("    ");
                showMessage("Row data" , asm );
            });        
        }

        addMenuItem("Show as column data (8 bits per column)..." , menu , () -> 
        {
            final String asm = "charset:\n; data organization: 8 bits per column columns\n"+spriteSet.getDataColumnsAsAssembly("    ",8)+
                    "\ncharset_mapping:\n"+spriteSet.getSpriteMappingAsAssembly("    ");            
            showMessage( "Column data" , asm  );
        }); 

        for ( Flip flip : Flip.values() ) 
        {
            addMenuItem("Show as column data ("+flip+")..." , menu , () -> 
            {
                final String asm = "charset:\n; data organization: columns "+flip+" \n"+spriteSet.getDataColumnsAsAssembly("    ",flip)+
                        "\ncharset_mapping:\n"+spriteSet.getSpriteMappingAsAssembly("    ");            
                showMessage( "Column data" , asm  );
            });            
        }        

        menu.addSeparator();
        addMenuItem("Quit" , menu , () -> System.exit(0) );

        setJMenuBar(menuBar);

        if ( currentFile != null ) 
        {
            setGlyphSet( loadGlyphSet( currentFile ) );
        }        
        
        updateWindowTitle();
    }

    private void showMessage(String title,String message) 
    {
        final JDialog dialog = new JDialog( (JFrame) null , title , true );
        final JTextArea area = new JTextArea();
        area.setEditable( false );
        area.setWrapStyleWord( true );
        area.setLineWrap( true );
        area.setText( message );
        final JScrollPane pane = new JScrollPane( area );
        pane.setPreferredSize( new Dimension(480, 240 ) );

        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        panel.add( pane , cnstrs );

        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener( ev -> dialog.dispose() );
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 0.0; cnstrs.weighty = 0;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0 ; cnstrs.gridy = 1;
        cnstrs.fill = GridBagConstraints.NONE;        
        panel.add( closeButton , cnstrs );

        dialog.getContentPane().add( panel );
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        area.setCaretPosition( 0 );
        dialog.setVisible( true );
        dialog.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
    }

    private void changeSpriteSize() 
    {
        final JTextField width = new JTextField();
        final JTextField height = new JTextField();
        width.setColumns( 5 );
        height.setColumns( 5 );

        width.setText( Integer.toString( previewPanel.currentSelection.getWidth() ) );
        height.setText( Integer.toString( previewPanel.currentSelection.getHeight() ) );

        final JDialog dialog = new JDialog( (JFrame) null , "Change sprite size" , true );

        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        // width
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;        
        cnstrs.fill = GridBagConstraints.NONE;
        panel.add( new JLabel("Width:"), cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.gridx = 1 ; cnstrs.gridy = 0;        
        cnstrs.fill = GridBagConstraints.NONE;
        panel.add( width , cnstrs );

        // height
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0 ; cnstrs.gridy = 1;        
        cnstrs.fill = GridBagConstraints.NONE;
        panel.add( new JLabel("Height:"), cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 2;
        cnstrs.gridx = 1 ; cnstrs.gridy = 1;        
        cnstrs.fill = GridBagConstraints.NONE;
        panel.add( height , cnstrs );       

        // close button
        final JButton closeButton = new JButton("Close");
        final ActionListener listener = ev -> 
        {
            final Integer w = asInt(width.getText() );
            final Integer h = asInt(height.getText() );
            if ( w != null && h != null && w.intValue() >= 1 && h.intValue() >= 1 && ! currentSelection().hasSize(w,h) )
            {
                currentSelection().resize(w,h,true);
                editorPanel.setSprite( currentSelection() );
                repaint();
            }
            dialog.dispose();
        };
        
        width.addActionListener( listener);
        height.addActionListener( listener);
        closeButton.addActionListener( listener);
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.95;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 3;
        cnstrs.gridx = 0 ; cnstrs.gridy = 2;        
        cnstrs.fill = GridBagConstraints.NONE;
        panel.add( closeButton , cnstrs );          

        // compose 
        dialog.getContentPane().add( panel );
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible( true );
        dialog.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
    }    
    
    private Sprite currentSelection() {
        return previewPanel.currentSelection;
    }

    private Integer asInt(String s) 
    {
        return s == null ? null : Integer.parseInt(s.trim());
    }

    private void setGlyphSet( SpriteSet set) 
    {
        if ( set != null ) {
            this.spriteSet = set;
            this.previewPanel.setGlyphSet( spriteSet );
            updateWindowTitle();
        }
    }

    private void addMenuItem(String label,JMenu menu, Runnable action) 
    {
        final JMenuItem item = new JMenuItem( label );
        menu.add( item );
        item.addActionListener( ev -> action.run() );
    }

    private SpriteSet loadGlyphSet() 
    {
        final File selected = askForFile( currentFile );
        if ( selected != null )
        {
            final SpriteSet result = loadGlyphSet( selected );
            saveConfig();
            return result;
        }
        return null;
    }
    
    private File askForFile(File preselected) 
    {
        final JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter( createFileFilter() );
        if ( preselected != null ) {
            chooser.setSelectedFile( preselected );
        }

        final int outcome = chooser.showOpenDialog( this );
        if ( outcome == JFileChooser.APPROVE_OPTION ) 
        {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private SpriteSet loadGlyphSet(File file) 
    {
        try (ObjectInputStream in = new ObjectInputStream( new FileInputStream( file ) ) ) 
        {
            final SpriteSet result = (SpriteSet) in.readObject();
            currentFile = file;
            return result;
        }
        catch(IOException | ClassNotFoundException e) 
        {
            e.printStackTrace();
        }
        return null;
    }

    private void saveConfig() 
    {
        final Properties props = new Properties();        
        if ( currentFile != null ) 
        {
            props.setProperty("lastFile" , currentFile.getAbsolutePath());
        }
        try ( FileOutputStream out = new FileOutputStream(CONFIG_FILE) ) {
            props.save(out,"AUTO-GENERATED AND WILL BE OVERWRITTEN,DO NOT EDIT");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() 
    {
        final Properties props = new Properties();
        if ( CONFIG_FILE.exists() ) 
        {
            try ( FileInputStream out = new FileInputStream(CONFIG_FILE) ) 
            {
                props.load( out );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        final String tmp = props.getProperty("lastFile");
        if ( tmp != null && tmp.trim().length() > 0 && new File(tmp).exists() ) {
            currentFile = new File(tmp);
        } else {
            currentFile = null;
        }
    }

    private FileFilter createFileFilter() 
    {
        return new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase().endsWith(".font");
            }

            @Override
            public String getDescription() {
                return ".font";
            }
        };
    }

    private void saveGlyphSet(SpriteSet set) 
    {
        if ( currentFile == null ) 
        {
            File file = pickFileToSave();
            if ( file == null ) {
                return;
            }
            currentFile = file;
        } 
        writeGlyphSet( set, currentFile );
        saveConfig();
    }

    private void writeGlyphSet(SpriteSet set,File output) 
    {
        try (ObjectOutputStream out = new ObjectOutputStream( new FileOutputStream( output  ) ) ) 
        {
            out.writeObject( set );
        }
        catch(IOException e) 
        {
            System.err.println("Failed to write to "+output.getAbsolutePath());
            e.printStackTrace();
        }
    }

    private File pickFileToSave() 
    {
        final JFileChooser chooser = new JFileChooser();
        final int outcome = chooser.showSaveDialog( this );
        if ( outcome == JFileChooser.APPROVE_OPTION ) 
        {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private final class PreviewPanel extends JPanel 
    {
        private final int yOffset = 10;
        private final int xOffset = 10;

        private final int previewWidth = 64;
        private final int previewHeight = 64;

        private SpriteSet spriteSet;

        private BufferedImage image;
        private Graphics2D imageGfx;

        private Sprite currentHighlight;
        private Sprite currentSelection;

        private final MouseAdapter mouseAdapter = new MouseAdapter() 
        {
            @Override
            public void mouseMoved(MouseEvent e) 
            {
                final int idx = getGlyphIndex(e);
                setHighlight( idx == -1 ? null : spriteSet.sprite( idx ) );
            }

            public void mouseClicked(MouseEvent e) 
            {
                if ( e.getButton() == MouseEvent.BUTTON1 ) 
                {
                    final int idx = getGlyphIndex( e );
                    if ( idx != -1 ) 
                    {
                        final Sprite sprite = spriteSet.sprite( idx );
                        if ( e.getClickCount() == 1 ) {
                            setCurrentSelection( sprite );
                        } else {
                            String text = Character.toString( (char) sprite.index() );
                            if ( ! sprite.hasIndex() ) 
                            {
                                text = "not set";
                            }
                            String result = JOptionPane.showInputDialog("Enter character mapping", text );
                            if ( result != null && result.length() > 0 ) 
                            {
                                sprite.setIndex( result.charAt(0) );
                                updateWindowTitle();
                                repaint();
                            }
                        }
                    }
                }
            }
        };

        public void setGlyphSet(SpriteSet g) 
        {
            this.spriteSet = g;
            this.currentSelection = spriteSet.sprite( 0 );
            this.currentHighlight = null;
            editorPanel.setSprite( this.currentSelection );
            image();
            repaint();
        }          

        private void deleteGlyph(Sprite sprite) 
        {
            if ( spriteSet.size() == 1 ) {
                return;
            }

            final int idx = spriteSet.indexOf( sprite );
            spriteSet.delete( sprite );
            Sprite newFocus = null;
            if ( spriteSet.size() > idx ) {
                newFocus = spriteSet.sprite( idx );
            } else if ( spriteSet.size() > (idx-1 ) ) {
                newFocus = spriteSet.sprite( idx-1 );
            }

            if ( Objects.equals( currentHighlight , sprite ) ) 
            {
                setHighlight( null );
            }

            if ( Objects.equals( currentSelection , sprite ) ) 
            {
                setCurrentSelection( newFocus );
            }
            repaint();
        }

        private void setHighlight(Sprite hl) 
        {
            final boolean highlightChanged = ! Objects.equals( currentHighlight , hl );
            if ( highlightChanged ) {
                currentHighlight = hl;
                repaint();
            }
        }        

        public PreviewPanel(SpriteSet set) 
        {
            this.spriteSet = set;
            this.currentSelection = spriteSet.sprite(0);

            setBackground( Color.BLACK );
            setFocusable(true);
            addKeyListener( new KeyAdapter() 
            {
                @Override
                public void keyReleased(KeyEvent e) 
                {
                    if ( e.getKeyCode() == KeyEvent.VK_G ) 
                    {
                        editorPanel.toggleRenderGrid();
                    } 
                    else if ( e.getKeyCode() == KeyEvent.VK_I ) 
                    {
                        currentSelection().invert();
                        previewPanel.repaint();
                        editorPanel.repaint();
                    }  
                    else if ( e.getKeyCode() == KeyEvent.VK_S ) 
                    {
                        changeSpriteSize();
                    }  
                    else if ( e.getKeyCode() == KeyEvent.VK_C ) 
                    {
                        currentSelection.clear();
                        editorPanel.setSprite( currentSelection );
                        repaint();
                    } 
                    else if ( e.getKeyCode() == KeyEvent.VK_PLUS ) 
                    {
                        Sprite newGlyph = new Sprite( currentSelection.getWidth() , currentSelection.getHeight() );
                        final int currentIdx = spriteSet.indexOf( currentSelection );
                        spriteSet.add( currentIdx+1 , newGlyph );
                        currentSelection = newGlyph;
                        editorPanel.setSprite( newGlyph );
                        repaint();
                    } 
                    else if ( e.getKeyCode() == KeyEvent.VK_DELETE ) 
                    {
                        if ( currentSelection != null ) 
                        {
                            final int result = JOptionPane.showConfirmDialog(null, "Really delete the current sprite ?");
                            if ( result == JOptionPane.YES_OPTION ) {
                                deleteGlyph( currentSelection );
                            }
                        }
                    } 
                    else if ( e.getKeyCode() == KeyEvent.VK_LEFT) 
                    {
                        if ( ( e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK ) != 0 )
                        {
                            spriteSet.moveBackwards( currentSelection );
                            repaint();
                        } else {
                            int idx = spriteSet.indexOf( currentSelection );
                            if ( idx > 0 ) {
                                setCurrentSelection( spriteSet.sprite( idx-1 ) );
                            }
                        }
                    } 
                    else if ( e.getKeyCode() == KeyEvent.VK_RIGHT) 
                    {
                        if ( ( e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK ) != 0 ) 
                        {
                            spriteSet.moveForwards( currentSelection );
                            repaint();
                        } else {
                            int idx = spriteSet.indexOf( currentSelection );
                            if ( idx+1 < spriteSet.size() ) {
                                setCurrentSelection( spriteSet.sprite( idx+1 ) );
                            }                            
                        }
                    }
                }
            });

            addMouseListener( mouseAdapter);
            addMouseMotionListener( mouseAdapter);
        }

        private int getGlyphIndex( MouseEvent e) 
        {
            if ( e.getX() >= xOffset && e.getY() >= yOffset && e.getY() <= yOffset+previewHeight ) 
            {
                int idx = (e.getX() - xOffset)/(previewWidth+xOffset);
                if ( idx >= 0 && idx < spriteSet.size() ) {
                    return idx;
                } 
            } 
            return -1;
        }

        public void setCurrentSelection(Sprite g) 
        {
            boolean selectionChanged = ! Objects.equals( this.currentSelection , g );
            if ( selectionChanged ) 
            {
                this.currentSelection = g;
                editorPanel.setSprite( g );
                updateWindowTitle();
                repaint();
            }
        }
        
        private BufferedImage image() 
        {
            final int width = spriteSet.size()*previewWidth + (spriteSet.size()+1)*xOffset;
            if ( image == null || image.getWidth() != width ) 
            {
                if ( image != null ) {
                    imageGfx.dispose();
                }
                image = new BufferedImage( width, previewHeight+20 + 15 , BufferedImage.TYPE_INT_RGB );
                imageGfx = image.createGraphics(); 
                setPreferredSize( new Dimension( width , yOffset + previewHeight+1 + 15 ) );
                getParent().revalidate();
            }
            return image;
        }

        private Graphics2D imageGfx() 
        {
            image();
            return imageGfx;
        }

        @Override
        protected void paintComponent(Graphics g) 
        {
            super.paintComponent(g);
            final Graphics2D gfx = imageGfx();
            gfx.setColor( Color.BLACK );
            gfx.fillRect( 0, 0 , image().getWidth() , image.getHeight() );
            for ( int i = 0 ; i < spriteSet.size() ; i++ ) 
            {
                final int x = i*previewWidth+(i+1)*xOffset;
                final AffineTransform oldTransform = gfx.getTransform();
                gfx.setTransform( AffineTransform.getTranslateInstance( x , 0 ) );
                final Sprite sprite = spriteSet.sprite( i );
                new GlyphRenderer( sprite ).renderGlyph( previewWidth , previewHeight , false , gfx );

                if ( sprite.equals( currentSelection ) ) 
                {
                    gfx.setColor(Color.RED);
                    gfx.drawRect(0,0,previewWidth,previewHeight);
                } 
                else if ( sprite.equals( currentHighlight ) ) 
                {
                    gfx.setColor(Color.BLUE);
                    gfx.drawRect(0,0,previewWidth,previewHeight);                    
                } 
                gfx.setTransform( oldTransform );

                final String txt2 = sprite.getWidth()+"x"+sprite.getHeight();
                if ( sprite.hasIndex() ) 
                {
                    final String txt1 = "'"+Character.toString( (char) sprite.index() )+"'";
                    gfx.drawString( txt1 , getCenterX(txt1,x) , previewHeight + 15 );
                    gfx.drawString( txt2 , getCenterX(txt2,x) , previewHeight + 15 + 15);
                } else {
                    gfx.drawString( txt2 , getCenterX(txt2,x) , previewHeight + 15 );
                }
            }
            g.drawImage( image() , xOffset , 0 , null );
        }
        
        private int getCenterX(String s,int spriteX0) 
        {
            final int width = getFontMetrics( getFont() ).stringWidth( s );
            return spriteX0+previewWidth/2-(width/2);
        }
    }

    private final class EditorPanel extends JPanel 
    {
        private float scalex,scaley;
        
        private boolean renderGrid = true;

        private Sprite sprite = new Sprite(8,8);

        final MouseAdapter mouseListener = new MouseAdapter() 
        {
            private boolean pressed;

            private int lastX=-1;
            private int lastY=-1;

            @Override
            public void mousePressed(MouseEvent e) 
            {
                if ( e.getButton() == MouseEvent.BUTTON1) 
                {
                    final int x = (int) Math.floor(e.getX() / scalex);
                    final int y = (int) Math.floor(e.getY() / scaley);
                    lastX = x ;
                    lastY = y;
                    pressed = true;
                    toggleCell(e);
                }
            }

            public void mouseDragged(MouseEvent e) 
            {
                if ( pressed ) 
                {
                    final int x = (int) Math.floor(e.getX() / scalex);
                    final int y = (int) Math.floor(e.getY() / scaley);
                    if ( x != lastX || y != lastY ) 
                    {
                        lastX = x;
                        lastY = y;
                        toggleCell(e);
                    }
                }
            }

            public void mouseReleased(MouseEvent e) 
            {
                pressed = false;
                lastX = lastY = -1;
            }

            private void toggleCell(MouseEvent e) 
            {
                final int x = (int) Math.floor(e.getX() / scalex);
                final int y = (int) Math.floor(e.getY() / scaley);
                if ( x >= 0 && y >= 0 && x < sprite.getWidth() && y < sprite.getHeight() ) {
                    sprite.togglePixel( x, y );
                    repaint();
                    previewPanel.repaint();
                }
            }            
        };

        {
            addMouseListener( mouseListener);
            addMouseMotionListener( mouseListener );
        }

        public void setSprite(Sprite sprite) {
            this.sprite = sprite;
            repaint();
        }

        public void toggleRenderGrid() {
            renderGrid = ! renderGrid;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) 
        {
            super.paintComponent(g);

            scalex = getWidth()  / (float) sprite.getWidth();
            scaley = getHeight() / (float) sprite.getHeight();

            new GlyphRenderer( sprite ).renderGlyph( getWidth() , getHeight() , renderGrid , g );
        }
    }

    public static class GlyphRenderer 
    {
        private float scalex,scaley;
        private final Sprite sprite;
        private int roundedScaleX;
        private int roundedScaleY;       

        public GlyphRenderer(Sprite g)
        {
            this.sprite = g;
        }

        public void renderGlyph(int width,int height,Graphics g) 
        {
            renderGlyph(width, height, true, g);
        }

        public void renderGlyph(int width,int height,boolean renderGrid, Graphics g) 
        {
            final int xmax = sprite.getWidth();
            final int ymax = sprite.getHeight();

            scalex = width  / (float) xmax;
            scaley = height / (float) ymax;
            
            roundedScaleX = (int) Math.ceil(scalex);
            roundedScaleY = (int) Math.ceil(scaley);            

            for ( int x = 0 ; x < xmax ; x++ ) 
            {
                for ( int y = 0 ; y < ymax ; y++ ) 
                {
                    renderCell(x,y,sprite.isSet(x,y),renderGrid,g);
                }                
            }            

            if ( ! renderGrid ) 
            {
                g.setColor( Color.WHITE );
                g.drawRect( 0 , 0 , width , height );
            }
        }

        private void renderCell(int x,int y,boolean set,boolean renderGrid,Graphics g) 
        {
            final int xMin = (int) Math.round(x*scalex);
            final int yMin = (int) Math.round(y*scaley);
            g.setColor(Color.BLACK);
            
            g.fillRect( xMin,yMin , roundedScaleX , roundedScaleY);
            g.setColor(Color.WHITE);
            if ( set ) 
            {
                g.fillRect( xMin,yMin , roundedScaleX , roundedScaleY);
            } 
            else 
            {
                if ( renderGrid ) {
                    g.drawRect( xMin,yMin , roundedScaleX , roundedScaleY);
                }
            }
        }
    }
    
    protected void loadFromImage() 
    {
        final File file = askForFile(null);
        if ( file != null && file.exists() && file.isFile() ) 
        {
            try {
                final BufferedImage image = ImageIO.read(file);
                currentSelection().setToImage( image );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void updateWindowTitle()
    {
        final Sprite sprite = currentSelection();
        String title = "Sprite "+spriteSet.indexOf( sprite )+" ( "+sprite.getWidth()+"x"+sprite.getHeight()+" )";
        if (  spriteSet.getSprites().stream().anyMatch( Sprite::hasIndex ) )
        {
            final String size;
            if ( spriteSet.isEmpty() ) {
                size = "";
            } else {
                final Dimension min = spriteSet.getMinSize(Sprite::hasIndex );
                final Dimension max = spriteSet.getMaxSize(Sprite::hasIndex );
                size = "("+min.width+"x"+min.getHeight()+") - ("+max.width+"x"+max.getHeight()+")";
            }
            final long count = spriteSet.getSprites().stream().filter( Sprite::hasIndex ).count();
            title += " | "+count+" sprites indexed , "+size;
        }
        setTitle( title );
    }    
}