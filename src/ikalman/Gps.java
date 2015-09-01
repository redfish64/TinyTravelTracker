package ikalman;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gps {
	private static final double EARTH_RADIUS_IN_MILES = 3963.1676;

	/*
	 * To use these functions:
	 * 
	 * 1. Start with a KalmanFilter created by alloc_filter_velocity2d. 2. At
	 * fixed intervals, call update_velocity2d with the lat/long. 3. At any
	 * time, to get an estimate for the current position, bearing, or speed, use
	 * the functions: get_lat_long get_bearing get_mph
	 */

	/*
	 * Create a GPS filter that only tracks two dimensions of position and
	 * velocity. The inherent assumption is that changes in velocity are
	 * randomly distributed around 0. Noise is a parameter you can use to alter
	 * the expected noise. 1.0 is the original, and the higher it is, the more a
	 * path will be "smoothed". Free with free_filter after using.
	 */
	public static KalmanFilter alloc_filter_velocity2d(double noise) {
		/*
		 * The state model has four dimensions: x, y, x', y' Each time step we
		 * can only observe position, not velocity, so the observation vector
		 * has only two dimensions.
		 */
		KalmanFilter f = new KalmanFilter(4, 2);

		/*
		 * Assuming the axes are rectilinear does not work well at the poles,
		 * but it has the bonus that we don't need to convert between lat/long
		 * and more rectangular coordinates. The slight inaccuracy of our
		 * physics model is not too important.
		 */
		double v2p = 0.001;
		Matrix.set_identity_matrix(f.state_transition);
		set_seconds_per_timestep(f, 1.0);

		/* We observe (x, y) in each time step */
		f.observation_model.setMatrix( 1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0);

		/* Noise in the world. */
		double pos = 0.000001;
		f.process_noise_covariance.setMatrix(
				 pos, 0.0, 0.0, 0.0 ,  0.0, pos, 0.0, 0.0 ,
				 0.0, 0.0, 1.0, 0.0 ,  0.0, 0.0, 0.0, 1.0  );

		/* Noise in our observation */
		f.observation_noise_covariance.setMatrix(
				 pos * noise, 0.0 ,  0.0, pos * noise  );

		/* The start position is totally unknown, so give a high variance */
		f.state_estimate.setMatrix(   0.0, 0.0, 0.0, 0.0  );
		Matrix.set_identity_matrix(f.estimate_covariance);
		double trillion = 1000.0 * 1000.0 * 1000.0 * 1000.0;
		Matrix.scale_matrix(f.estimate_covariance, trillion);

		return f;
	}

	/* Set the seconds per timestep in the velocity2d model. */
	/*
	 * The position units are in thousandths of latitude and longitude. The
	 * velocity units are in thousandths of position units per second.
	 * 
	 * So if there is one second per timestep, a velocity of 1 will change the
	 * lat or long by 1 after a million timesteps.
	 * 
	 * Thus a typical position is hundreds of thousands of units. A typical
	 * velocity is maybe ten.
	 */
	static void set_seconds_per_timestep(KalmanFilter f, double seconds_per_timestep) {
		/*
		 * unit_scaler accounts for the relation between position and velocity
		 * units
		 */
		double unit_scaler = 0.001;
		f.state_transition.data[0][2] = unit_scaler * seconds_per_timestep;
		f.state_transition.data[1][3] = unit_scaler * seconds_per_timestep;
	}

	/* Update the velocity2d model with new gps data. */
	public static void update_velocity2d(KalmanFilter f, double lat, double lon,
			double seconds_since_last_timestep) {
		set_seconds_per_timestep(f, seconds_since_last_timestep);
		f.observation.data[0][0] = lat * 1000.0;
		f.observation.data[1][0] = lon * 1000.0;
		f.update();
	}

	/*
	 * Read a lat,long pair from a file. Format is lat,long<ignored> Return
	 * whether there was a lat,long to be read
	 */
	static boolean read_lat_long(BufferedReader file, double[] latLonOut) throws IOException {
		Pattern p = Pattern.compile("(\\d+),(\\d+)");

		while (true) {
			String line = file.readLine();
			if (line == null)
				return false;

			Matcher m = p.matcher(line);

			if (m.matches()) {
				latLonOut[0] = Double.parseDouble(m.group(1));
				latLonOut[1] = Double.parseDouble(m.group(2));
				return true;
			}

		}
	}

	/* Extract a lat long from a velocity2d Kalman filter. */
	public static void get_lat_long(KalmanFilter f, double[] latLonOut) {
		latLonOut[0] = f.state_estimate.data[0][0] / 1000.0;
		latLonOut[1] = f.state_estimate.data[1][0] / 1000.0;
	}

	/*
	 * Extract velocity with lat-long-per-second units from a velocity2d Kalman
	 * filter.
	 */
	static void get_velocity(KalmanFilter f, double[] delta_lat_lon_out) {
		delta_lat_lon_out[0] = f.state_estimate.data[2][0] / (1000.0 * 1000.0);
		delta_lat_lon_out[1] = f.state_estimate.data[3][0] / (1000.0 * 1000.0);
	}

	/*
	 * Extract a bearing from a velocity2d Kalman filter. 0 = north, 90 = east,
	 * 180 = south, 270 = west
	 */
	/*
	 * See http://www.movable-type.co.uk/scripts/latlong.html for formulas
	 */
	static double get_bearing(KalmanFilter f) {
		double[] out = new double[2];

		double lat, lon, delta_lat, delta_lon, x, y;
		get_lat_long(f, out);
		lat = out[0];
		lon = out[1];

		get_velocity(f, out);
		delta_lat = out[0];
		delta_lon = out[1];

		/* Convert to radians */
		double to_radians = Math.PI / 180.0;
		lat *= to_radians;
		lon *= to_radians;
		delta_lat *= to_radians;
		delta_lon *= to_radians;

		/* Do math */
		double lat1 = lat - delta_lat;
		y = Math.sin(delta_lon) * Math.cos(lat);
		x = Math.cos(lat1) * Math.sin(lat) - Math.sin(lat1) * Math.cos(lat)
				* Math.cos(delta_lon);
		double bearing = Math.atan2(y, x);

		/* Convert to degrees */
		bearing = bearing / to_radians;
		while (bearing >= 360.0) {
			bearing -= 360.0;
		}
		while (bearing < 0.0) {
			bearing += 360.0;
		}

		return bearing;
	}

	/* Convert a lat, long, delta lat, and delta long into mph. */
	static double calculate_mph(double lat, double lon, double delta_lat,
			double delta_lon) {
		/*
		 * First, let's calculate a unit-independent measurement - the radii of
		 * the earth traveled in each second. (Presumably this will be a very
		 * small number.)
		 */

		/* Convert to radians */
		double to_radians = Math.PI / 180.0;
		lat *= to_radians;
		lon *= to_radians;
		delta_lat *= to_radians;
		delta_lon *= to_radians;

		/* Haversine formula */
		double lat1 = lat - delta_lat;
		double sin_half_dlat = Math.sin(delta_lat / 2.0);
		double sin_half_dlon = Math.sin(delta_lon / 2.0);
		double a = sin_half_dlat * sin_half_dlat + Math.cos(lat1)
				* Math.cos(lat) * sin_half_dlon * sin_half_dlon;
		double radians_per_second = 2 * Math.atan2(1000.0 * Math.sqrt(a),
				1000.0 * Math.sqrt(1.0 - a));

		/* Convert units */
		double miles_per_second = radians_per_second * EARTH_RADIUS_IN_MILES;
		double miles_per_hour = miles_per_second * 60.0 * 60.0;
		return miles_per_hour;
	}

	/* Extract speed in miles per hour from a velocity2d Kalman filter. */
	static double get_mph(KalmanFilter f) {
		double[] latLon = new double[2];
		double[] deltaLatLon = new double[2];
		get_lat_long(f, latLon);
		get_velocity(f, deltaLatLon);
		return calculate_mph(latLon[0], latLon[1], deltaLatLon[0],
				deltaLatLon[1]);
	}

}
