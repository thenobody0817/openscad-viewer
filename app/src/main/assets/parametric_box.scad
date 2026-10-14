// Parametric box — OpenSCAD Viewer demo model.
// Drag the sliders in Parameters and watch the viewport update.

/* [Box dimensions] */
// Outer width in mm
box_width = 50;    // [10:5:200]
// Outer depth in mm
box_depth = 30;    // [10:5:200]
// Outer height in mm
box_height = 20;   // [5:5:100]

/* [Lid] */
// How the lid attaches
lid_style = "snap";   // [snap, screw, hinge]
// Render the lid at all
show_lid = true;

/* [Hidden] */
wall = 2;
eps = 0.01;

module shell(w, d, h) {
    difference() {
        cube([w, d, h], center = true);
        translate([0, 0, wall])
            cube([w - 2*wall, d - 2*wall, h], center = true);
    }
}

shell(box_width, box_depth, box_height);
if (show_lid) translate([0, 0, box_height/2 + 6])
    cube([box_width, box_depth, 3], center = true);
