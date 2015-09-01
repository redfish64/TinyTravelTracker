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

run("cp -r $proj_dir/libs $temp_dir");
run("cp -r $proj_dir/src $temp_dir");
run("cp -r $proj_dir/res $temp_dir");
run("cp -r $proj_dir/gen $temp_dir");
run("cp -r $proj_dir/assets $temp_dir");
system("cp $proj_dir/* $temp_dir");

my @src_files = get_files("$temp_dir/src","*.java");
my @xml_files = get_files("$temp_dir/res","*.xml");
my @libs = get_files("$temp_dir/libs","*jar");

replace_in_files(\@xml_files,
		 ['xmlns:app="http:\/\/schemas.android.com\/apk\/res\/.*?"', 'xmlns:app="http:\/\/schemas.android.com\/apk\/res\/$chosen_package"'],
		 ["<\\!--\\s*ttt_installer:${pt}_disable_comment.*", ''],
		 [".*ttt_installer:${pt}_disable_comment\\s*-->", '']
);

replace_in_files(["$temp_dir/AndroidManifest.xml"], ['package=".*?"','package="$chosen_package"'],
    ['android:sharedUserId=".*?"', 'android:sharedUserId="$premium_pkg"'],

		 #if there are any items that begin with ".", it will mess up the installer, 
		 #everything must be fully qualified "com.rareventure.gps.<class>
		 ['name="\.', undef ],  #undef means to die
		 ['android:icon="\@drawable\/.*?"', 'android:icon="\@drawable\/icon_${pt}"']
    );

#<application android:icon="@drawable/icon" android:label="
replace_in_files(
    ["$temp_dir/res/values/strings.xml"],
    ['<string name="app_name">.*?<\/string>', '<string name="app_name">$chosen_name<\/string>'],
    ['<string name="reviewer_app_name">.*?<\/string>', '<string name="reviewer_app_name">${chosen_name}<\/string>']
    );

replace_in_files(\@src_files,
		 ['import com\.rareventure.*\.R;',""],
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

#we do release so that we can test proguard
#if($real) {
  run("ant release");
#}
#else {
  #as opposed to release, this signs with a debug key and does not use proguard
#  run("ant debug");
#}



run("mkdir -p $temp_dir/gen/$slash_package");

#to really sign, jarsigner and zipalign as follows:

if($real)
{
    run("rm -f $temp_dir/real.apk");

    run("rm -r -f $proj_dir/proguard_bin_${pt}");

    run("cp -r $temp_dir/bin/proguard $proj_dir/proguard_bin_${pt}");

    print <<EoF ;
To really sign, run:

jarsigner -keystore ~/notes/llc/rareventure.keystore -verbose -sigalg MD5withRSA -digestalg SHA1  $temp_dir/bin/GpsTrailerReviewerStart-release-unsigned.apk rareventure
zipalign -v 4 $temp_dir/bin/GpsTrailerReviewerStart-release-unsigned.apk $temp_dir/real.apk
cp $temp_dir/real.apk $proj_dir/proguard_bin_${pt}/

#Then to install, run:

adb -d install -r $temp_dir/real.apk

EoF

    exit 0;
}




#co: we are using "ant debug" which should do this for us
run("jarsigner -storepass android -keystore ~/.android/debug.keystore bin/GpsTrailerReviewerStart-release-unsigned.apk androiddebugkey");


run("zipalign -v 4 bin/GpsTrailerReviewerStart-release-unsigned.apk foo2.apk");

print "Run:\n\nadb -d install -r $temp_dir/foo2.apk\n";
#print "Run:\n\nadb -d install -r /tmp/android_install/bin/GpsTrailerReviewerStart-debug.apk\n";



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

