package mil.nga.giat.geopackage.sample;

import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import mil.nga.giat.geopackage.GeoPackage;
import mil.nga.giat.geopackage.GeoPackageException;
import mil.nga.giat.geopackage.GeoPackageManager;
import mil.nga.giat.geopackage.core.contents.Contents;
import mil.nga.giat.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.giat.geopackage.core.srs.SpatialReferenceSystemDao;
import mil.nga.giat.geopackage.factory.GeoPackageFactory;
import mil.nga.giat.geopackage.features.columns.GeometryColumns;
import mil.nga.giat.geopackage.features.user.FeatureDao;
import mil.nga.giat.geopackage.tiles.matrix.TileMatrix;
import mil.nga.giat.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.giat.geopackage.tiles.user.TileDao;
import mil.nga.giat.geopackage.user.UserColumn;
import mil.nga.giat.geopackage.user.UserTable;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Manager Fragment, import, view, select, edit GeoPackages
 * 
 * @author osbornb
 * 
 */
public class GeoPackageManagerFragment extends Fragment {

	/**
	 * Get a new fragment instance
	 * 
	 * @param active
	 * @return
	 */
	public static GeoPackageManagerFragment newInstance(
			GeoPackageDatabases active) {
		GeoPackageManagerFragment listFragment = new GeoPackageManagerFragment(
				active);
		return listFragment;
	}

	/**
	 * Active GeoPackages
	 */
	private GeoPackageDatabases active;

	/**
	 * Expandable list adapter
	 */
	private GeoPackageListAdapter adapter = new GeoPackageListAdapter();

	/**
	 * List of databases
	 */
	private List<String> databases = new ArrayList<String>();

	/**
	 * List of database tables within each database
	 */
	private List<List<GeoPackageTable>> databaseTables = new ArrayList<List<GeoPackageTable>>();

	/**
	 * Layout inflater
	 */
	private LayoutInflater inflater;

	/**
	 * GeoPackage manager
	 */
	private GeoPackageManager manager;

	/**
	 * Constructor
	 */
	public GeoPackageManagerFragment() {

	}

	/**
	 * Constructor
	 * 
	 * @param active
	 */
	public GeoPackageManagerFragment(GeoPackageDatabases active) {
		this.active = active;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		this.inflater = inflater;
		manager = GeoPackageFactory.getManager(getActivity());
		View v = inflater.inflate(R.layout.fragment_manager, null);
		ExpandableListView elv = (ExpandableListView) v
				.findViewById(R.id.fragment_manager_view_ui);
		elv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				int itemType = ExpandableListView.getPackedPositionType(id);
				if (itemType == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
					int childPosition = ExpandableListView
							.getPackedPositionChild(id);
					int groupPosition = ExpandableListView
							.getPackedPositionGroup(id);
					tableOptions(databaseTables.get(groupPosition).get(
							childPosition));
					return true;
				} else if (itemType == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
					int groupPosition = ExpandableListView
							.getPackedPositionGroup(id);
					databaseOptions(databases.get(groupPosition));
					return true;
				}
				return false;
			}
		});
		elv.setAdapter(adapter);
		update();
		return v;
	}

	/**
	 * Update the listing of databases and tables
	 */
	public void update() {
		databases = manager.databases();
		databaseTables.clear();
		for (String database : databases) {
			GeoPackage geoPackage = manager.open(database);
			List<GeoPackageTable> tables = new ArrayList<GeoPackageTable>();
			for (String tableName : geoPackage.getFeatureTables()) {
				FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
				int count = featureDao.count();
				GeoPackageTable table = GeoPackageTable.createFeature(database,
						tableName, count);
				table.setActive(active.exists(table));
				tables.add(table);
			}
			for (String tableName : geoPackage.getTileTables()) {
				TileDao tileDao = geoPackage.getTileDao(tableName);
				int count = tileDao.count();
				GeoPackageTable table = GeoPackageTable.createTile(database,
						tableName, count);
				table.setActive(active.exists(table));
				tables.add(table);
			}
			databaseTables.add(tables);
			geoPackage.close();
		}

		adapter.notifyDataSetChanged();
	}

	/**
	 * Show options for the GeoPackage database
	 * 
	 * @param database
	 */
	private void databaseOptions(final String database) {

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.select_dialog_item);
		adapter.add(getString(R.string.geopackage_view_label));
		adapter.add(getString(R.string.geopackage_delete_label));
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(database);
		builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {

				if (item >= 0) {

					switch (item) {
					case 0:
						viewDatabaseOption(database);
						break;
					case 1:
						deleteDatabaseOption(database);
						break;
					default:
					}
				}
			}
		});

		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * View database information
	 * 
	 * @param database
	 */
	private void viewDatabaseOption(final String database) {
		StringBuilder databaseInfo = new StringBuilder();
		GeoPackage geoPackage = manager.open(database);
		try {
			SpatialReferenceSystemDao srsDao = geoPackage
					.getSpatialReferenceSystemDao();

			List<SpatialReferenceSystem> srsList = srsDao.queryForAll();
			databaseInfo.append("Feature Tables: ").append(
					geoPackage.getFeatureTables().size());
			databaseInfo.append("\nTile Tables: ").append(
					geoPackage.getTileTables().size());
			databaseInfo.append("\n\nSpatial Reference Systems: ").append(
					srsList.size());
			for (SpatialReferenceSystem srs : srsList) {
				databaseInfo.append("\n");
				addSrs(databaseInfo, srs);
			}

		} catch (SQLException e) {
			databaseInfo.append(e.getMessage());
		} finally {
			geoPackage.close();
		}
		AlertDialog viewDialog = new AlertDialog.Builder(getActivity())
				.setTitle(database).setPositiveButton("OK",

				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setMessage(databaseInfo.toString()).create();
		viewDialog.show();
	}

	/**
	 * Add Spatial Reference System to the info
	 * 
	 * @param info
	 * @param srs
	 */
	private void addSrs(StringBuilder info, SpatialReferenceSystem srs) {
		info.append("\nSRS Name: ").append(srs.getSrsName());
		info.append("\nSRS ID: ").append(srs.getSrsId());
		info.append("\nOrganization: ").append(srs.getOrganization());
		info.append("\nCoordsys ID: ").append(srs.getOrganizationCoordsysId());
		info.append("\nDefinition: ").append(srs.getDefinition());
		info.append("\nDescription: ").append(srs.getDescription());
	}

	/**
	 * Delete database alert option
	 * 
	 * @param database
	 */
	private void deleteDatabaseOption(final String database) {
		AlertDialog deleteDialog = new AlertDialog.Builder(getActivity())
				.setTitle(getString(R.string.geopackage_delete_label))
				.setMessage("Are you sure you want to delete " + database)
				.setPositiveButton("Delete",

				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						manager.delete(database);
						active.removeDatabase(database);
						update();
					}
				})

				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).create();
		deleteDialog.show();
	}

	/**
	 * Show options for the GeoPackage table
	 * 
	 * @param table
	 */
	private void tableOptions(final GeoPackageTable table) {

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.select_dialog_item);
		adapter.add(getString(R.string.geopackage_table_view_label));
		adapter.add(getString(R.string.geopackage_table_delete_label));
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(table.getDatabase() + " - " + table.getName());
		builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int item) {

				if (item >= 0) {

					switch (item) {
					case 0:
						viewTableOption(table);
						break;
					case 1:
						deleteTableOption(table);
						break;

					default:
					}
				}
			}
		});

		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * View table information
	 * 
	 * @param table
	 */
	private void viewTableOption(final GeoPackageTable table) {
		StringBuilder info = new StringBuilder();
		GeoPackage geoPackage = manager.open(table.getDatabase());
		try {
			Contents contents = null;
			FeatureDao featureDao = null;
			TileDao tileDao = null;
			UserTable<? extends UserColumn> userTable = null;
			if (table.isFeature()) {
				featureDao = geoPackage.getFeatureDao(table.getName());
				contents = featureDao.getGeometryColumns().getContents();
				info.append("Feature Table");
				info.append("\nFeatures: ").append(featureDao.count());
				userTable = featureDao.getTable();
			} else {
				tileDao = geoPackage.getTileDao(table.getName());
				contents = tileDao.getTileMatrixSet().getContents();
				info.append("Tile Table");
				info.append("\nZoom Levels: ").append(
						tileDao.getTileMatrices().size());
				info.append("\nTiles: ").append(tileDao.count());
				userTable = tileDao.getTable();
			}

			SpatialReferenceSystem srs = contents.getSrs();

			info.append("\n\nSpatial Reference System:");
			addSrs(info, srs);

			info.append("\n\nContents:");
			info.append("\nTable Name: ").append(contents.getTableName());
			info.append("\nData Type: ").append(contents.getDataType());
			info.append("\nIdentifier: ").append(contents.getIdentifier());
			info.append("\nDescription: ").append(contents.getDescription());
			info.append("\nLast Change: ").append(contents.getLastChange());
			info.append("\nMin X: ").append(contents.getMinX());
			info.append("\nMin Y: ").append(contents.getMinY());
			info.append("\nMax X: ").append(contents.getMaxX());
			info.append("\nMax Y: ").append(contents.getMaxY());

			if (featureDao != null) {
				GeometryColumns geometryColumns = featureDao
						.getGeometryColumns();
				info.append("\n\nGeometry Columns:");
				info.append("\nTable Name: ").append(
						geometryColumns.getTableName());
				info.append("\nColumn Name: ").append(
						geometryColumns.getColumnName());
				info.append("\nGeometry Type Name: ").append(
						geometryColumns.getGeometryTypeName());
				info.append("\nZ: ").append(geometryColumns.getZ());
				info.append("\nM: ").append(geometryColumns.getM());
			}

			if (tileDao != null) {
				TileMatrixSet tileMatrixSet = tileDao.getTileMatrixSet();
				info.append("\n\nTile Matrix Set:");
				info.append("\nTable Name: ").append(
						tileMatrixSet.getTableName());
				info.append("\nMin X: ").append(tileMatrixSet.getMinX());
				info.append("\nMin Y: ").append(tileMatrixSet.getMinY());
				info.append("\nMax X: ").append(tileMatrixSet.getMaxX());
				info.append("\nMax Y: ").append(tileMatrixSet.getMaxY());

				info.append("\n\nTile Matrices:");
				for (TileMatrix tileMatrix : tileDao.getTileMatrices()) {
					info.append("\n\nTable Name: ").append(
							tileMatrix.getTableName());
					info.append("\nZoom Level: ").append(
							tileMatrix.getZoomLevel());
					info.append("\nMatrix Width: ").append(
							tileMatrix.getMatrixWidth());
					info.append("\nMatrix Height: ").append(
							tileMatrix.getMatrixHeight());
					info.append("\nTile Width: ").append(
							tileMatrix.getTileWidth());
					info.append("\nTile Height: ").append(
							tileMatrix.getTileHeight());
					info.append("\nPixel X Size: ").append(
							tileMatrix.getPixelXSize());
					info.append("\nPixel Y Size: ").append(
							tileMatrix.getPixelYSize());
				}
			}

			info.append("\n\n").append(table.getName()).append(" columns:");
			for (UserColumn userColumn : userTable.getColumns()) {
				info.append("\n\nIndex: ").append(userColumn.getIndex());
				info.append("\nName: ").append(userColumn.getName());
				if (userColumn.getMax() != null) {
					info.append("\nMax: ").append(userColumn.getMax());
				}
				info.append("\nNot Null: ").append(userColumn.isNotNull());
				if (userColumn.getDefaultValue() != null) {
					info.append("\nDefault Value: ").append(
							userColumn.getDefaultValue());
				}
				if (userColumn.isPrimaryKey()) {
					info.append("\nPrimary Key: ").append(
							userColumn.isPrimaryKey());
				}
				info.append("\nType: ").append(userColumn.getTypeName());
			}

		} catch (GeoPackageException e) {
			info.append(e.getMessage());
		} finally {
			geoPackage.close();
		}
		AlertDialog viewDialog = new AlertDialog.Builder(getActivity())
				.setTitle(table.getDatabase() + " - " + table.getName())
				.setPositiveButton("OK",

				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setMessage(info.toString()).create();
		viewDialog.show();
	}

	/**
	 * Delete table alert option
	 * 
	 * @param table
	 */
	private void deleteTableOption(final GeoPackageTable table) {
		AlertDialog deleteDialog = new AlertDialog.Builder(getActivity())
				.setTitle(getString(R.string.geopackage_table_delete_label))
				.setMessage(
						"Are you sure you want to delete "
								+ table.getDatabase() + " - " + table.getName())
				.setPositiveButton("Delete",

				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						GeoPackage geoPackage = manager.open(table
								.getDatabase());
						try {
							geoPackage.deleteTable(table.getName());
							active.removeTable(table);
							update();
						} catch (Exception e) {
							showMessage("Delete " + table.getDatabase() + " "
									+ table.getName() + " Table",
									e.getMessage());
						} finally {
							geoPackage.close();
						}
					}
				})

				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).create();
		deleteDialog.show();
	}

	/**
	 * Handle manager menu clicks
	 * 
	 * @param item
	 * @return
	 */
	public boolean handleMenuClick(MenuItem item) {
		boolean handled = true;

		switch (item.getItemId()) {
		case R.id.import_geopackage_url:
			importGeopackageFromUrl();
			break;
		case R.id.import_geopackage_file:
			importGeopackageFromFile();
			break;
		default:
			handled = false;
			break;
		}

		return handled;
	}

	/**
	 * Import a GeoPackage from a URL
	 */
	private void importGeopackageFromUrl() {

		LayoutInflater inflater = LayoutInflater.from(getActivity());
		View importUrlView = inflater.inflate(R.layout.import_url, null);
		AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
		dialog.setView(importUrlView);

		final EditText nameInput = (EditText) importUrlView
				.findViewById(R.id.import_url_name_input);
		final EditText urlInput = (EditText) importUrlView
				.findViewById(R.id.import_url_input);
		final Button button = (Button) importUrlView
				.findViewById(R.id.import_url_preloaded);
		button.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(
						getActivity(), android.R.layout.select_dialog_item);
				adapter.addAll(getResources().getStringArray(
						R.array.preloaded_url_labels));
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle(getString(R.string.import_url_preloaded_label));
				builder.setAdapter(adapter,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int item) {
								if (item >= 0) {
									String[] urls = getResources()
											.getStringArray(
													R.array.preloaded_urls);
									String[] names = getResources()
											.getStringArray(
													R.array.preloaded_url_names);
									nameInput.setText(names[item]);
									urlInput.setText(urls[item]);
								}
							}
						});

				AlertDialog alert = builder.create();
				alert.show();
			}
		});

		dialog.setPositiveButton("Import",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int id) {
						DownloadTask downloadTask = new DownloadTask();
						downloadTask.execute(nameInput.getText().toString(),
								urlInput.getText().toString());
					}
				}).setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		dialog.show();

	}

	/**
	 * Download a GeoPackage from a URL in the background
	 */
	private class DownloadTask extends AsyncTask<String, Integer, String> {

		public DownloadTask() {
		}

		@Override
		protected String doInBackground(String... params) {
			try {
				String name = params[0];
				URL url = new URL(params[1]);
				if (manager.importGeoPackage(name, url)) {
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							update();
						}
					});
				} else {
					throw new Exception("Failed to import GeoPackage '" + name
							+ "' at url '" + url + "'");
				}
			} catch (final Exception e) {
				try {
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							showMessage("URL Import", e.getMessage());
						}
					});
				} catch (Exception e2) {
					// eat
				}
			}
			return null;
		}
	}

	/**
	 * Import a GeoPackage from a file
	 */
	private void importGeopackageFromFile() {
		// TODO
		Toast.makeText(getActivity(), "Not yet supported", Toast.LENGTH_SHORT)
				.show();
	}

	/**
	 * Show a message with an OK button
	 * 
	 * @param title
	 * @param message
	 */
	private void showMessage(String title, String message) {
		if (title != null || message != null) {
			new AlertDialog.Builder(getActivity())
					.setTitle(title != null ? title : "")
					.setMessage(message != null ? message : "")
					.setNeutralButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							}).show();
		}
	}

	/**
	 * Expandable list adapter
	 */
	public class GeoPackageListAdapter extends BaseExpandableListAdapter {

		@Override
		public int getGroupCount() {
			return databases.size();
		}

		@Override
		public int getChildrenCount(int i) {
			return databaseTables.get(i).size();
		}

		@Override
		public Object getGroup(int i) {
			return databases.get(i);
		}

		@Override
		public Object getChild(int i, int j) {
			return databaseTables.get(i).get(j);
		}

		@Override
		public long getGroupId(int i) {
			return i;
		}

		@Override
		public long getChildId(int i, int j) {
			return j;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public View getGroupView(int i, boolean isExpanded, View view,
				ViewGroup viewGroup) {
			if (view == null) {
				view = inflater.inflate(R.layout.manager_group, null);
			}

			TextView geoPackageName = (TextView) view
					.findViewById(R.id.manager_group_name);
			geoPackageName.setText(databases.get(i));

			return view;
		}

		@Override
		public View getChildView(int i, int j, boolean b, View view,
				ViewGroup viewGroup) {
			if (view == null) {
				view = inflater.inflate(R.layout.manager_child, null);
			}

			final GeoPackageTable table = databaseTables.get(i).get(j);

			CheckBox checkBox = (CheckBox) view
					.findViewById(R.id.manager_child_checkbox);
			ImageView imageView = (ImageView) view
					.findViewById(R.id.manager_child_image);
			TextView tableName = (TextView) view
					.findViewById(R.id.manager_child_name);
			TextView count = (TextView) view
					.findViewById(R.id.manager_child_count);

			checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView,
						boolean isChecked) {
					if (table.isActive() != isChecked) {
						table.setActive(isChecked);
						if (isChecked) {
							active.addTable(table);
						} else {
							active.removeTable(table);
						}
					}
				}
			});
			tableName.setOnLongClickListener(new View.OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					GeoPackageManagerFragment.this.tableOptions(table);
					return true;
				}
			});

			checkBox.setChecked(table.isActive());
			if (table.isFeature()) {
				imageView.setImageDrawable(getResources().getDrawable(
						R.drawable.ic_features));
			} else {
				imageView.setImageDrawable(getResources().getDrawable(
						R.drawable.ic_tiles));
			}

			tableName.setText(table.getName());
			count.setText("(" + String.valueOf(table.getCount()) + ")");

			return view;
		}

		@Override
		public boolean isChildSelectable(int i, int j) {
			return true;
		}

	}

}