package ikalman;


/**
 * Jama = Java Matrix class.
 * <P>
 * The Java Matrix Class provides the fundamental operations of numerical linear
 * algebra. Various constructors create Matrices from two dimensional arrays of
 * double precision floating point numbers. Various "gets" and "sets" provide
 * access to submatrices and matrix elements. Several methods implement basic
 * matrix arithmetic, including matrix addition and multiplication, matrix
 * norms, and element-by-element array operations. Methods for reading and
 * printing matrices are also included. All the operations in this version of
 * the Matrix Class involve real matrices. Complex matrices may be handled in a
 * future version.
 * <P>
 * Five fundamental matrix decompositions, which consist of pairs or triples of
 * matrices, permutation vectors, and the like, produce results in five
 * decomposition classes. These decompositions are accessed by the Matrix class
 * to compute solutions of simultaneous linear equations, determinants, inverses
 * and other matrix functions. The five decompositions are:
 * <P>
 * <UL>
 * <LI>Cholesky Decomposition of symmetric, positive definite matrices.
 * <LI>LU Decomposition of rectangular matrices.
 * <LI>QR Decomposition of rectangular matrices.
 * <LI>Singular Value Decomposition of rectangular matrices.
 * <LI>Eigenvalue Decomposition of both symmetric and nonsymmetric square
 * matrices.
 * </UL>
 * <DL>
 * <DT><B>Example of use:</B></DT>
 * <P>
 * <DD>Solve a linear system A x = b and compute the residual norm, ||b - A x||.
 * <P>
 * 
 * <PRE>
 * double[][] vals = { { 1., 2., 3 }, { 4., 5., 6. }, { 7., 8., 10. } };
 * Matrix A = new Matrix(vals);
 * Matrix b = Matrix.random(3, 1);
 * Matrix x = A.solve(b);
 * Matrix r = A.times(x).minus(b);
 * double rnorm = r.normInf();
 * </PRE>
 * 
 * </DD>
 * </DL>
 * 
 * @author The MathWorks, Inc. and the National Institute of Standards and
 *         Technology.
 * @version 5 August 1998
 */

public class Matrix implements Cloneable, java.io.Serializable {

	/*
	 * ------------------------ Class variables ------------------------
	 */

	/**
	 * Array for internal storage of elements.
	 * 
	 * @serial internal array storage.
	 */
	double[][] data;

	/**
	 * Row and column dimensions.
	 * 
	 * @serial row dimension.
	 * @serial column dimension.
	 */
	private int rows, cols;

	/*
	 * ------------------------ Constructors ------------------------
	 */

	/**
	 * Construct an m-by-n matrix of zeros.
	 * 
	 * @param m
	 *            Number of rows.
	 * @param n
	 *            Number of colums.
	 */

	public Matrix(int m, int n) {
		this.rows = m;
		this.cols = n;
		data = new double[m][n];
	}

	/**
	 * Construct an m-by-n constant matrix.
	 * 
	 * @param m
	 *            Number of rows.
	 * @param n
	 *            Number of colums.
	 * @param s
	 *            Fill the matrix with this scalar value.
	 */

	public Matrix(int m, int n, double s) {
		this.rows = m;
		this.cols = n;
		data = new double[m][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				data[i][j] = s;
			}
		}
	}

	/**
	 * Construct a matrix from a 2-D array.
	 * 
	 * @param A
	 *            Two-dimensional array of doubles.
	 * @exception IllegalArgumentException
	 *                All rows must have the same length
	 * @see #constructWithCopy
	 */

	public Matrix(double[][] A) {
		rows = A.length;
		cols = A[0].length;
		for (int i = 0; i < rows; i++) {
			if (A[i].length != cols) {
				throw new IllegalArgumentException(
						"All rows must have the same length.");
			}
		}
		this.data = A;
	}

	/**
	 * Construct a matrix quickly without checking arguments.
	 * 
	 * @param A
	 *            Two-dimensional array of doubles.
	 * @param m
	 *            Number of rows.
	 * @param n
	 *            Number of colums.
	 */

	public Matrix(double[][] A, int m, int n) {
		this.data = A;
		this.rows = m;
		this.cols = n;
	}

	/**
	 * Construct a matrix from a one-dimensional packed array
	 * 
	 * @param vals
	 *            One-dimensional array of doubles, packed by columns (ala
	 *            Fortran).
	 * @param m
	 *            Number of rows.
	 * @exception IllegalArgumentException
	 *                Array length must be a multiple of m.
	 */

	public Matrix(double vals[], int m) {
		this.rows = m;
		cols = (m != 0 ? vals.length / m : 0);
		if (m * cols != vals.length) {
			throw new IllegalArgumentException(
					"Array length must be a multiple of m.");
		}
		data = new double[m][cols];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < cols; j++) {
				data[i][j] = vals[i + j * m];
			}
		}
	}

	/*
	 * ------------------------ Public Methods ------------------------
	 */

	/**
	 * Construct a matrix from a copy of a 2-D array.
	 * 
	 * @param A
	 *            Two-dimensional array of doubles.
	 * @exception IllegalArgumentException
	 *                All rows must have the same length
	 */

	public static Matrix constructWithCopy(double[][] A) {
		int m = A.length;
		int n = A[0].length;
		Matrix X = new Matrix(m, n);
		double[][] C = X.getArray();
		for (int i = 0; i < m; i++) {
			if (A[i].length != n) {
				throw new IllegalArgumentException(
						"All rows must have the same length.");
			}
			for (int j = 0; j < n; j++) {
				C[i][j] = A[i][j];
			}
		}
		return X;
	}

	/**
	 * Make a deep copy of a matrix
	 */

	public Matrix copy() {
		Matrix X = new Matrix(rows, cols);
		double[][] C = X.getArray();
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				C[i][j] = data[i][j];
			}
		}
		return X;
	}

	/**
	 * Clone the Matrix object.
	 */

	public Object clone() {
		return this.copy();
	}

	/**
	 * Access the internal two-dimensional array.
	 * 
	 * @return Pointer to the two-dimensional array of matrix elements.
	 */

	public double[][] getArray() {
		return data;
	}

	/**
	 * Copy the internal two-dimensional array.
	 * 
	 * @return Two-dimensional array copy of matrix elements.
	 */

	public double[][] getArrayCopy() {
		double[][] C = new double[rows][cols];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				C[i][j] = data[i][j];
			}
		}
		return C;
	}

	/**
	 * Make a one-dimensional column packed copy of the internal array.
	 * 
	 * @return Matrix elements packed in a one-dimensional array by columns.
	 */

	public double[] getColumnPackedCopy() {
		double[] vals = new double[rows * cols];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				vals[i + j * rows] = data[i][j];
			}
		}
		return vals;
	}

	/**
	 * Make a one-dimensional row packed copy of the internal array.
	 * 
	 * @return Matrix elements packed in a one-dimensional array by rows.
	 */

	public double[] getRowPackedCopy() {
		double[] vals = new double[rows * cols];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				vals[i * cols + j] = data[i][j];
			}
		}
		return vals;
	}

	/**
	 * Get row dimension.
	 * 
	 * @return m, the number of rows.
	 */

	public int getRowDimension() {
		return rows;
	}

	/**
	 * Get column dimension.
	 * 
	 * @return n, the number of columns.
	 */

	public int getColumnDimension() {
		return cols;
	}

	/**
	 * Get a single element.
	 * 
	 * @param i
	 *            Row index.
	 * @param j
	 *            Column index.
	 * @return A(i,j)
	 * @exception ArrayIndexOutOfBoundsException
	 */

	public double get(int i, int j) {
		return data[i][j];
	}

	/**
	 * Get a submatrix.
	 * 
	 * @param i0
	 *            Initial row index
	 * @param i1
	 *            Final row index
	 * @param j0
	 *            Initial column index
	 * @param j1
	 *            Final column index
	 * @return A(i0:i1,j0:j1)
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public Matrix getMatrix(int i0, int i1, int j0, int j1) {
		Matrix X = new Matrix(i1 - i0 + 1, j1 - j0 + 1);
		double[][] B = X.getArray();
		try {
			for (int i = i0; i <= i1; i++) {
				for (int j = j0; j <= j1; j++) {
					B[i - i0][j - j0] = data[i][j];
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
		return X;
	}

	/**
	 * Get a submatrix.
	 * 
	 * @param r
	 *            Array of row indices.
	 * @param c
	 *            Array of column indices.
	 * @return A(r(:),c(:))
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public Matrix getMatrix(int[] r, int[] c) {
		Matrix X = new Matrix(r.length, c.length);
		double[][] B = X.getArray();
		try {
			for (int i = 0; i < r.length; i++) {
				for (int j = 0; j < c.length; j++) {
					B[i][j] = data[r[i]][c[j]];
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
		return X;
	}

	/**
	 * Get a submatrix.
	 * 
	 * @param i0
	 *            Initial row index
	 * @param i1
	 *            Final row index
	 * @param c
	 *            Array of column indices.
	 * @return A(i0:i1,c(:))
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public Matrix getMatrix(int i0, int i1, int[] c) {
		Matrix X = new Matrix(i1 - i0 + 1, c.length);
		double[][] B = X.getArray();
		try {
			for (int i = i0; i <= i1; i++) {
				for (int j = 0; j < c.length; j++) {
					B[i - i0][j] = data[i][c[j]];
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
		return X;
	}

	/**
	 * Get a submatrix.
	 * 
	 * @param r
	 *            Array of row indices.
	 * @param j0
	 *            Initial column index
	 * @param j1
	 *            Final column index
	 * @return A(r(:),j0:j1)
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public Matrix getMatrix(int[] r, int j0, int j1) {
		Matrix X = new Matrix(r.length, j1 - j0 + 1);
		double[][] B = X.getArray();
		try {
			for (int i = 0; i < r.length; i++) {
				for (int j = j0; j <= j1; j++) {
					B[i][j - j0] = data[r[i]][j];
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
		return X;
	}

	/**
	 * Set a single element.
	 * 
	 * @param i
	 *            Row index.
	 * @param j
	 *            Column index.
	 * @param s
	 *            A(i,j).
	 * @exception ArrayIndexOutOfBoundsException
	 */

	public void set(int i, int j, double s) {
		data[i][j] = s;
	}

	/**
	 * Set a submatrix.
	 * 
	 * @param i0
	 *            Initial row index
	 * @param i1
	 *            Final row index
	 * @param j0
	 *            Initial column index
	 * @param j1
	 *            Final column index
	 * @param X
	 *            A(i0:i1,j0:j1)
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public void setMatrix(int i0, int i1, int j0, int j1, Matrix X) {
		try {
			for (int i = i0; i <= i1; i++) {
				for (int j = j0; j <= j1; j++) {
					data[i][j] = X.get(i - i0, j - j0);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
	}

	/**
	 * Set a submatrix.
	 * 
	 * @param r
	 *            Array of row indices.
	 * @param c
	 *            Array of column indices.
	 * @param X
	 *            A(r(:),c(:))
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public void setMatrix(int[] r, int[] c, Matrix X) {
		try {
			for (int i = 0; i < r.length; i++) {
				for (int j = 0; j < c.length; j++) {
					data[r[i]][c[j]] = X.get(i, j);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
	}

	/**
	 * Set a submatrix.
	 * 
	 * @param r
	 *            Array of row indices.
	 * @param j0
	 *            Initial column index
	 * @param j1
	 *            Final column index
	 * @param X
	 *            A(r(:),j0:j1)
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public void setMatrix(int[] r, int j0, int j1, Matrix X) {
		try {
			for (int i = 0; i < r.length; i++) {
				for (int j = j0; j <= j1; j++) {
					data[r[i]][j] = X.get(i, j - j0);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
	}

	/**
	 * Set a submatrix.
	 * 
	 * @param i0
	 *            Initial row index
	 * @param i1
	 *            Final row index
	 * @param c
	 *            Array of column indices.
	 * @param X
	 *            A(i0:i1,c(:))
	 * @exception ArrayIndexOutOfBoundsException
	 *                Submatrix indices
	 */

	public void setMatrix(int i0, int i1, int[] c, Matrix X) {
		try {
			for (int i = i0; i <= i1; i++) {
				for (int j = 0; j < c.length; j++) {
					data[i][c[j]] = X.get(i - i0, j);
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ArrayIndexOutOfBoundsException("Submatrix indices");
		}
	}

	public static void copy_matrix(Matrix source, Matrix destination) {
		assert (source.rows == destination.rows);
		assert (source.cols == destination.cols);
		for (int i = 0; i < source.rows; ++i) {
			for (int j = 0; j < source.cols; ++j) {
				destination.data[i][j] = source.data[i][j];
			}
		}
	}

	public static void print_matrix(Matrix m) {
		for (int i = 0; i < m.rows; ++i) {
			for (int j = 0; j < m.cols; ++j) {
				if (j > 0) {
					System.out.printf(" ");
				}
				System.out.printf("%6.2f", m.data[i][j]);
			}
			System.out.printf("\n");
		}
	}

	public static void add_matrix(Matrix a, Matrix b, Matrix c) {
		assert (a.rows == b.rows);
		assert (a.rows == c.rows);
		assert (a.cols == b.cols);
		assert (a.cols == c.cols);
		for (int i = 0; i < a.rows; ++i) {
			for (int j = 0; j < a.cols; ++j) {
				c.data[i][j] = a.data[i][j] + b.data[i][j];
			}
		}
	}

	public static void subtract_matrix(Matrix a, Matrix b, Matrix c) {
		assert (a.rows == b.rows);
		assert (a.rows == c.rows);
		assert (a.cols == b.cols);
		assert (a.cols == c.cols);
		for (int i = 0; i < a.rows; ++i) {
			for (int j = 0; j < a.cols; ++j) {
				c.data[i][j] = a.data[i][j] - b.data[i][j];
			}
		}
	}

	public static void subtract_from_identity_matrix(Matrix a) {
		assert (a.rows == a.cols);
		for (int i = 0; i < a.rows; ++i) {
			for (int j = 0; j < a.cols; ++j) {
				if (i == j) {
					a.data[i][j] = 1.0 - a.data[i][j];
				} else {
					a.data[i][j] = 0.0 - a.data[i][j];
				}
			}
		}
	}

	public static void multiply_matrix(Matrix a, Matrix b, Matrix c) {
		assert (a.cols == b.rows);
		assert (a.rows == c.rows);
		assert (b.cols == c.cols);
		for (int i = 0; i < c.rows; ++i) {
			for (int j = 0; j < c.cols; ++j) {
				/*
				 * Calculate element c.data[i][j] via a dot product of one row
				 * of a with one column of b
				 */
				c.data[i][j] = 0.0;
				for (int k = 0; k < a.cols; ++k) {
					c.data[i][j] += a.data[i][k] * b.data[k][j];
				}
			}
		}
	}

	/*
	 * This is multiplying a by b-tranpose so it is like multiply_matrix but
	 * references to b reverse rows and cols.
	 */
	public static void multiply_by_transpose_matrix(Matrix a, Matrix b, Matrix c) {
		assert (a.cols == b.cols);
		assert (a.rows == c.rows);
		assert (b.rows == c.cols);
		for (int i = 0; i < c.rows; ++i) {
			for (int j = 0; j < c.cols; ++j) {
				/*
				 * Calculate element c.data[i][j] via a dot product of one row
				 * of a with one row of b
				 */
				c.data[i][j] = 0.0;
				for (int k = 0; k < a.cols; ++k) {
					c.data[i][j] += a.data[i][k] * b.data[j][k];
				}
			}
		}
	}

	public static void transpose_matrix(Matrix input, Matrix output) {
		assert (input.rows == output.cols);
		assert (input.cols == output.rows);
		for (int i = 0; i < input.rows; ++i) {
			for (int j = 0; j < input.cols; ++j) {
				output.data[j][i] = input.data[i][j];
			}
		}
	}

	public static boolean equal_matrix(Matrix a, Matrix b, double tolerance) {
		assert (a.rows == b.rows);
		assert (a.cols == b.cols);
		for (int i = 0; i < a.rows; ++i) {
			for (int j = 0; j < a.cols; ++j) {
				if (Math.abs(a.data[i][j] - b.data[i][j]) > tolerance) {
					return false;
				}
			}
		}
		return true;
	}

	public static void scale_matrix(Matrix m, double scalar) {
		assert (scalar != 0.0);
		for (int i = 0; i < m.rows; ++i) {
			for (int j = 0; j < m.cols; ++j) {
				m.data[i][j] *= scalar;
			}
		}
	}

	public static void swap_rows(Matrix m, int r1, int r2) {
		assert (r1 != r2);
		double[] tmp = m.data[r1];
		m.data[r1] = m.data[r2];
		m.data[r2] = tmp;
	}

	public static void scale_row(Matrix m, int r, double scalar) {
		assert (scalar != 0.0);
		for (int i = 0; i < m.cols; ++i) {
			m.data[r][i] *= scalar;
		}
	}

	/* Add scalar * row r2 to row r1. */
	public static void shear_row(Matrix m, int r1, int r2, double scalar) {
		assert (r1 != r2);
		for (int i = 0; i < m.cols; ++i) {
			m.data[r1][i] += scalar * m.data[r2][i];
		}
	}

	/*
	 * Uses Gauss-Jordan elimination.
	 * 
	 * The elimination procedure works by applying elementary row operations to
	 * our input matrix until the input matrix is reduced to the identity
	 * matrix. Simultaneously, we apply the same elementary row operations to a
	 * separate identity matrix to produce the inverse matrix. If this makes no
	 * sense, read wikipedia on Gauss-Jordan elimination.
	 * 
	 * This is not the fastest way to invert matrices, so this is quite possibly
	 * the bottleneck.
	 */
	public static boolean destructive_invert_matrix(Matrix input, Matrix output) {
		assert (input.rows == input.cols);
		assert (input.rows == output.rows);
		assert (input.rows == output.cols);

		set_identity_matrix(output);

		/*
		 * Convert input to the identity matrix via elementary row operations.
		 * The ith pass through this loop turns the element at i,i to a 1 and
		 * turns all other elements in column i to a 0.
		 */
		for (int i = 0; i < input.rows; ++i) {
			if (input.data[i][i] == 0.0) {
				/* We must swap rows to get a nonzero diagonal element. */
				int r;
				for (r = i + 1; r < input.rows; ++r) {
					if (input.data[r][i] != 0.0) {
						break;
					}
				}
				if (r == input.rows) {
					/*
					 * Every remaining element in this column is zero, so this
					 * matrix cannot be inverted.
					 */
					return false;
				}
				swap_rows(input, i, r);
				swap_rows(output, i, r);
			}

			/*
			 * Scale this row to ensure a 1 along the diagonal. We might need to
			 * worry about overflow from a huge scalar here.
			 */
			double scalar = 1.0 / input.data[i][i];
			scale_row(input, i, scalar);
			scale_row(output, i, scalar);

			/* Zero out the other elements in this column. */
			for (int j = 0; j < input.rows; ++j) {
				if (i == j) {
					continue;
				}
				double shear_needed = -input.data[j][i];
				shear_row(input, j, i, shear_needed);
				shear_row(output, j, i, shear_needed);
			}
		}

		return true;
	}

	public static void set_identity_matrix(Matrix m) {
		assert (m.rows == m.cols);
		for (int i = 0; i < m.rows; ++i) {
			for (int j = 0; j < m.cols; ++j) {
				if (i == j) {
					m.data[i][j] = 1.0;
				} else {
					m.data[i][j] = 0.0;
				}
			}
		}
	}

	public void setMatrix(double[][] ds) {
		this.data = ds;
		this.rows = ds.length;
		this.cols = ds[0].length;
	}

	public void setMatrix(double ... d) {
		  for (int i = 0; i < rows; ++i) {
			    for (int j = 0; j < cols; ++j) {
			      data[i][j] = d[j+cols*i];
			    }
			  }
	}
}
