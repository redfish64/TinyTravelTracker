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
    print "
Usage: $0 <output directory> <images_desc_file>
Sizes images according to image_desc_file
";

    exit -1;
}

my $output_dir = shift @ARGV;

my $file = shift @ARGV;

open(F, $file) || die "$file!";

while($_=<F>)
{
    chop $_;
    s/#.*//;
    s/^\s*(.*?)\s*$/$1/;
    
    if(/^$/)
    {
	next;
    }
    elsif(/^(.*):(.*)/)
    {
	my ($file, $geom) = ($1, $2);

	run("convert -resize $geom $file $output_dir/$file");
    }
}

sub run
{
    print "Running: ".(join " ",@_)."\n";
    system @_;
}
