#    Copyright 2015 Tim Engler, Rareventure LLC
#
#    This file is part of Tiny Travel Tracker.
#
#    Tiny Travel Tracker is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, either version 3 of the License, or
#    (at your option) any later version.
#
#    Tiny Travel Tracker is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with Tiny Travel Tracker.  If not, see <http://www.gnu.org/licenses/>.
#

#!/usr/bin/perl

if(@ARGV == 0)
{
    print <<EoF ;
Usage: $0 [-real] <android home> <eclipse project dir> <trial name> <real name> <trial package name> <premium package name> <trial|premium>
EoF
    exit -1;
}

if($ARGV[0] eq "-real")
{
    shift @ARGV;
    $real = 1;
}

#replace_in_files(["/tmp/t.java"], ['\/\* ttt_installer:obfuscate_str \*\/"(.*?)"',
#				 'obsfucate($1)','e' ]);

my $android_home = shift @ARGV;
my $proj_dir = shift @ARGV;
my $trial_name = shift @ARGV;
my $real_name = shift @ARGV;
my $trial_pkg = shift @ARGV;
my $premium_pkg = shift @ARGV;
my $pt = shift @ARGV;

my $chosen_name;
my $is_premium;
my $chosen_package;
if($pt eq "premium")
{
    $is_premium = 1;
    $chosen_package = $premium_pkg;
    $chosen_name = $real_name;
}
elsif($pt eq "trial")
{
    $is_premium = 0;
    $chosen_package = $trial_pkg;
    $chosen_name = $trial_name;
}
else { die "Must be either trial or premium, got $pt"; }

my $temp_dir = "/tmp/android_install";
my $slash_package = $chosen_package;
$slash_package =~ s/\./\//g;

print "Creating $pt package (ignore omitting directory errors)\n";

run("rm -rf $temp_dir");
run("mkdir $temp_dir");
run("mkdir $temp_dir/app");

run("cp -r $proj_dir/app/libs $temp_dir/app");
run("cp -r $proj_dir/app/src $temp_dir/app");
run("cp -r $proj_dir/gradle $temp_dir/gradle");
system("cp $proj_dir/app/* $temp_dir/app");
system("cp $proj_dir/* $temp_dir/");

my ($version_name,$version_code) = read_from_file("$temp_dir/app/src/main/AndroidManifest.xml",
						  'android:versionName="(.*?)"',
						  'android:versionCode="(.*?)"'
    );

print "VERSION IS $version_name, $version_code\n";


my @rv_src_files = (get_files("$temp_dir/app/src/main/java/com/rareventure","*.java"),
		    get_files("$temp_dir/app/src/main/java/com/lamerman","*.java"),
		    get_files("$temp_dir/app/src/main/java/com/codeslap","*.java"));
my @xml_files = get_files("$temp_dir/app/src/main/res","*.xml");
my @libs = get_files("$temp_dir/app/libs","*jar");
my @gradle_files = get_files("$temp_dir/","build.gradle");

replace_in_files(\@gradle_files,
		 ['com.rareventure.gps2','$chosen_package']);

replace_in_files(\@xml_files,
		 ['xmlns:app="http:\/\/schemas.android.com\/apk\/res\/.*?"', 'xmlns:app="http:\/\/schemas.android.com\/apk\/res\/$chosen_package"'],
		 ["<\\!--\\s*ttt_installer:${pt}_disable_comment.*", ''],
		 [".*ttt_installer:${pt}_disable_comment\\s*-->", '']
);

replace_in_files(["$temp_dir/app/src/main/AndroidManifest.xml"], ['package=".*?"','package="$chosen_package"'],
    ['android:sharedUserId=".*?"', 'android:sharedUserId="$premium_pkg"'],

		 #if there are any items that begin with ".", it will mess up the installer, 
		 #everything must be fully qualified "com.rareventure.gps.<class>
		 ['name="\.', undef ],  #undef means to die
		 ['android:icon="\@drawable\/.*?"', 'android:icon="\@drawable\/icon_${pt}"']
    );

replace_in_files(["$temp_dir/app/src/main/java/com/rareventure/gps2/reviewer/AboutScreen.java"],
		 ['BuildConfig\.VERSION_NAME',"\"$version_name\""],
		 ['BuildConfig\.VERSION_CODE',$version_code],
		 ['import com.rareventure.gps2.BuildConfig;','']
    );

#<application android:icon="@drawable/icon" android:label="
replace_in_files(
    ["$temp_dir/app/src/main/res/values/strings.xml"],
    ['<string name="app_name">.*?<\/string>', '<string name="app_name">$chosen_name<\/string>'],
    ['<string name="reviewer_app_name">.*?<\/string>', '<string name="reviewer_app_name">${chosen_name}<\/string>']
    );

replace_in_files(\@rv_src_files,
		 ['import com\.rareventure.*\.R;',""],
#		 ['import com\.rareventure\.gps2\.R\.(.*?);','import $chosen_package.R.$1;'],
#		 ['import com\.rareventure\.gps2\.R\;','import $chosen_package.R;'],
		 ['^(package .*;)','$1;import $chosen_package.R;',""],
#		 ['\/\*android_install_frozen_version:external_dir\*\/"\/(.*)?\/"', '\/*android_install_frozen_version:external_dir*\/"\/TinyTravelTracker_$chosen_package\/"'],
		 ['\/\* ttt_installer:premium_neg42 \*\/-?\d+', $is_premium ? -42 : -89 ],
		 ['\/\* ttt_installer:premium_package \*\/"(.*)?"',"\"$premium_pkg\"" ],
		 ['\/\* ttt_installer:trial_package \*\/"(.*)?"',"\"$trial_pkg\"" ],
		 # /* ttt_installer:remove_line */
		 ['\/\* ttt_installer:remove_line \*\/','\/\/' ],
		 ['\/\* ttt_installer:obfuscate_str \*\/"(.*?)"',
		  'obsfucate($1)','e' ] #warning, this creates objects and is slow

    );




chdir $temp_dir;

if($real)
{
    use Term::ReadKey;
    print "Type your password:";
    ReadMode('noecho'); # don't echo
    chomp(my $password = <STDIN>);
    ReadMode(0);        # back to normal

    system("./gradlew","assembleRelease","-Pandroid.injected.signing.store.file=/home/tim/notes/llc/rareventure.keystore","-Pandroid.injected.signing.key.alias=rareventure","-Pandroid.injected.signing.store.password=$password","-Pandroid.injected.signing.key.password=$password");
    print <<EoF ;
#To install, run:

adb -d install -r $temp_dir/app/build/outputs/apk/app-release.apk

EoF

    exit 0;
}
else
{
    system("./gradlew","assembleRelease");
}


sub get_files
{
    my ($dir,$glob) = @_;
    my @files = map {chop; $_;} (`find $dir -name '$glob'`);

    return @files;
}

sub replace_in_files
{
    my ($files, @pat_rep) = @_;
    my %matchCount;

    foreach my $pat_rep (@pat_rep)
    {
	my ($pat, $rep, $ext) = @$pat_rep;

	if(defined $rep) # if $rep not defined, then it's a check for bad data
	{
	    $matchCount{"s/$pat/$rep/$ext"} = 0;
	}
    }

    foreach $file (@$files)
    {
#	print "Processing $file...";
	$bakfile = $file.".bak";
	run("cp","$file","$bakfile");
	
	open(IN,$bakfile) || die "Couldn't open $bakfile for reading";
	open(OUT,">".$file) || die "Couldn't open $file for writing";

	my $line;

	while($line = <IN>)
	{
	    foreach my $pat_rep (@pat_rep)
	    {
		my ($pat, $rep, $ext) = @$pat_rep;
		
		#if we are looking for a pattern that we are checking that doesn't exist in the file
		if(!defined $rep)
		{
		    if(eval("\$line =~ /$pat/"))
		    {
			die "Bad pattern found in file, '$pat'";
		    }
		}
		    
		if($ext eq undef)
		{
		    $ext = "";
		}
    
		if(eval("\$line =~ s/$pat/$rep/$ext;"))
		{
		    $matchCount{"s/$pat/$rep/$ext"} ++;
		}
		
	    }

	    print OUT $line;
	}			

	close IN;
	close OUT;
	

	run("rm","$bakfile");
    }

    foreach my $pat (keys %matchCount)
    {
	my $matchCount = $matchCount{$pat};
	
	if($matchCount >= 1)
	{
	    print "Processing $file...replaced $matchCount matches for $pat\n";	
	}
	else
	{
	    die "No matches for $pat\n";
	}
    }
    
}


sub run
{
    #print join(" ",@_)."\n";
    system(@_) == 0 || die join(" ",@_);
}


sub obsfucate
{
    my ($arg) = $1;

    my $v = 0;
    my $s = (int(rand(128)));

    return "com.rareventure.android.Util.unobfuscate(new byte [] { $s, ".
	join(", ", 
	     map { $v = ($v ^ ord($_) ^ $s ^ 42); } (split //, $arg))." })";
}


sub read_from_file
{
    my ($file,@regexps) = @_;

    my @out;

    open(IN,$file) || die "Couldn't open $file for reading";

    my $l;

    while($l = <IN>)
    {
	for(my $i = 0; $i < @regexps; $i++)
	{
	    my $regexp = $regexps[$i];

	    if($l =~ m/$regexp/)
	    {
		$out[$i] = $1;
	    }
	}
    }

    return @out;
}
