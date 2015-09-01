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
Usage: $0 <android home> <eclipse project dir> <chosen package> <name>
EoF
    exit -1;
}

my $android_home = shift @ARGV;
my $proj_dir = shift @ARGV;
my $chosen_package = shift @ARGV;
my $chosen_name = shift @ARGV;
my $temp_dir = "/tmp/android_install";
my $slash_package = $chosen_package;
$slash_package =~ s/\./\//g;

run("rm -rf $temp_dir");
run("mkdir $temp_dir");

run("cp -r $proj_dir/libs $temp_dir");
run("cp -r $proj_dir/src $temp_dir");
run("cp -r $proj_dir/res $temp_dir");
run("cp -r $proj_dir/gen $temp_dir");
system("cp $proj_dir/* $temp_dir");

my @src_files = get_files("$temp_dir/src","*.java");
my @xml_files = get_files("$temp_dir/res","*.xml");
my @libs = get_files("$temp_dir/libs","*jar");

replace_in_files('package=".*?"','package="$chosen_package"',"","$temp_dir/AndroidManifest.xml");
#<application android:icon="@drawable/icon" android:label="
replace_in_files(
    ["$temp_dir/res/values/strings.xml"],
    ['<string name="app_name">.*?<\/string>', '<string name="app_name">$chosen_name<\/string>'],
    ['<string name="reviewer_app_name">.*?<\/string>', '<string name="reviewer_app_name">${chosen_name}<\/string>']);

replace_in_files(\@src_files,
		 ['import com\.rareventure.*\.R;',""],
		 ['^(package .*;)','$1\nimport $chosen_package.R;\n',""],
		 ['\/\* ttt_installer:release_mode \*\/false', '\/\* ttt_installer:release_mode \*\/true'],
		 ['\/\*android_install_frozen_version:external_dir\*\/"\/(.*)?\/"', '\/*android_install_frozen_version:external_dir*\/"\/TinyTravelTracker_$chosen_package\/"']);
replace_in_files(\@xml_files,
		 ['xmlns:app="http:\/\/schemas.android.com\/apk\/res\/.*?"', 'xmlns:app="http:\/\/schemas.android.com\/apk\/res\/$chosen_package"']);

chdir $temp_dir;

#we do release so that we can test proguard
run("ant release");

run("mkdir -p $temp_dir/gen/$slash_package");

run("jarsigner -storepass android -keystore ~/.android/debug.keystore bin/GpsTrailerReviewerStart-release-unsigned.apk androiddebugkey");


run("zipalign -v 4 bin/GpsTrailerReviewerStart-release-unsigned.apk foo2.apk");

print "Run:\n\nadb -d install -r $temp_dir/foo2.apk\n";



sub get_files
{
    my ($dir,$glob) = @_;
    my @files = map {chop; $_;} (`find $dir -name '$glob'`);

    return @files;
}

sub replace_in_files
{
    my ($files, @pat_rep) = @_;
    foreach $file (@$files)
    {
#	print "Processing $file...";
	$bakfile = $file.".bak";
	run("cp","$file","$bakfile");
	
	open(IN,$bakfile) || die "Couldn't open $bakfile for reading";
	open(OUT,">".$file) || die "Couldn't open $file for writing";

	my $line;
	my $matchCount = 0;

	while($line = <IN>)
	{
	    foreach my $pat_rep (@pat_rep)
	    {
		my ($pat, $rep, $ext) = \@$pat_rep;
		
		if($ext == undef)
		{
		    $ext = "";
		}
    
		if(eval("\$line =~ s/$pat/$rep/$ext;"))
		{
		    $matchCount ++;
		}
		
	    }

	    if($matchCount >= 0)
	    {
		print "Processing $file...replaced $matchCount matches\n";	
	    }
	    
	    print OUT $line;
	}			

	close IN;
	close OUT;
	

	run("rm","$bakfile");
    }
}


sub run
{
    print join(" ",@_)."\n";
    system(@_) == 0 || die join(" ",@_);
}
