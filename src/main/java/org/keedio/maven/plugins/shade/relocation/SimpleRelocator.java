package org.keedio.maven.plugins.shade.relocation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.codehaus.plexus.util.SelectorUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Jason van Zyl
 * @author Mauro Talevi
 */
public class SimpleRelocator
    implements Relocator
{

	protected final String pattern;

	protected final String pathPattern;

	protected final String shadedPattern;

	protected final String shadedPathPattern;

    protected final Set<String> includes;

    protected final Set<String> excludes;

    protected final boolean rawString;

	
    public SimpleRelocator( String patt, String shadedPattern, Set<File> includes, List<String> excludes)
    {
        this( patt, shadedPattern, includes, excludes, false );
    }

    public SimpleRelocator( String patt, String shadedPattern, Set<File> includes, List<String> excludes, 
                            boolean rawString )
    {
        this.rawString = rawString;

        if ( rawString )
        {
            this.pathPattern = patt;
            this.shadedPathPattern = shadedPattern;

            this.pattern = null; // not used for raw string relocator
            this.shadedPattern = null; // not used for raw string relocator
        }
        else
        {
            if ( patt == null )
            {
                this.pattern = "";
                this.pathPattern = "";
            }
            else
            {
                this.pattern = patt.replace( '/', '.' );
                this.pathPattern = patt.replace( '.', '/' );
            }

            if ( shadedPattern != null )
            {
                this.shadedPattern = shadedPattern.replace( '/', '.' );
                this.shadedPathPattern = shadedPattern.replace( '.', '/' );
            }
            else
            {
                this.shadedPattern = "hidden." + this.pattern;
                this.shadedPathPattern = "hidden/" + this.pathPattern;
            }
        }

        this.includes = new HashSet();
        this.excludes = normalizePatterns(excludes);
        
		try {
			for (File jar:includes) {
				ZipInputStream zip = new ZipInputStream(new FileInputStream(jar));
				for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
				        // This ZipEntry represents a class. Now, what class does it represent?
				        String className = entry.getName().replace('/', '.'); // including ".class"
				        this.includes.add(className.substring(0, className.length() - ".class".length()));
				    }
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();;
		}        

    }

    private static Set<String> normalizePatterns( Collection<String> patterns )
    {
        Set<String> normalized = null;

        if ( patterns != null && !patterns.isEmpty() )
        {
            normalized = new LinkedHashSet<String>();

            for ( String pattern : patterns )
            {

                String classPattern = pattern.replace( '.', '/' );

                normalized.add( classPattern );

                if ( classPattern.endsWith( "/*" ) )
                {
                    String packagePattern = classPattern.substring( 0, classPattern.lastIndexOf( '/' ) );
                    normalized.add( packagePattern );
                }
            }
        }

        return normalized;
    }

    private boolean isIncluded( String path )
    {
        if ( includes != null && !includes.isEmpty() )
        {
        	return includes.contains(path.replace( '/', '.' ));
        }
        return false;
    }

    private boolean isExcluded( String path )
    {
        if ( excludes != null && !excludes.isEmpty() )
        {
            for ( String exclude : excludes )
            {
                if ( SelectorUtils.matchPath( exclude, path, true ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canRelocatePath( String path )
    {
        if ( rawString )
        {
            return Pattern.compile( pathPattern ).matcher( path ).find();
        }

        if ( path.endsWith( ".class" ) )
        {
            path = path.substring( 0, path.length() - 6 );
        }

        if ( !isIncluded( path ) || isExcluded( path ) )
        {
            return false;
        }

        // Allow for annoying option of an extra / on the front of a path. See MSHADE-119; comes from
        // getClass().getResource("/a/b/c.properties").
        return path.startsWith( pathPattern ) || path.startsWith ( "/" + pathPattern );
    }

    public boolean canRelocateClass( String clazz )
    {
        return !rawString && clazz.indexOf( '/' ) < 0 && canRelocatePath( clazz.replace( '.', '/' ) );
    }

    public String relocatePath( String path )
    {
        if ( rawString )
        {
            return path.replaceAll( pathPattern, shadedPathPattern );
        }
        else
        {
            return path.replaceFirst( pathPattern, shadedPathPattern );
        }
    }

    public String relocateClass( String clazz )
    {
        return clazz.replaceFirst( pattern, shadedPattern );
    }

    public String applyToSourceContent( String sourceContent )
    {
        if ( rawString )
        {
            return sourceContent;
        }
        else
        {
            return sourceContent.replaceAll( "\\b" + pattern, shadedPattern );
        }
    }
}