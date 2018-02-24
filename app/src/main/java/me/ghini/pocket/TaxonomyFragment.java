package me.ghini.pocket;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import static me.ghini.pocket.MainActivity.GENUS;

/**
 * Created by mario on 2018-01-30.
 */

public class TaxonomyFragment extends android.support.v4.app.Fragment {

    List<String> stringList;  // think of it as the model.
    ArrayAdapter<String> listAdapter;  // and this would be the presenter

    // change this static field to calculate the metaphone codes
    public static boolean recomputeMetaphone = false;
    EditText taxonomySearch = null;

    public TaxonomyFragment() {
        // once and for all.
        stringList = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_taxonomy, container, false);
        ListView mainListView = rootView.findViewById(R.id.taxonomy_results);

        // Create ArrayAdapter using the string list.  think of it as the presenter.
        // see how it's being handled a model and a view.
        listAdapter = new ArrayAdapter<>(
                rootView.getContext(), R.layout.textrow, stringList);

        // Set the ArrayAdapter as the ListView's adapter.
        mainListView.setAdapter( listAdapter );

        taxonomySearch = rootView.findViewById(R.id.taxonomy_search);
        taxonomySearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    lookupName(((EditText)v).getText().toString());
                }
            }
        });
        taxonomySearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent keyEvent) {
                if(actionId == EditorInfo.IME_ACTION_DONE) {
                    lookupName(((EditText)v).getText().toString());
                }
                return false;
            }
        });
        return rootView;
    }

    Integer addLineFromCursor(Cursor cr) {
        String epithet = cr.getString(0);
        String authorship = cr.getString(1);
        Integer acceptedId = cr.getInt(2);
        Integer parentId = cr.getInt(3);
        Boolean isSynonym = false;
        if (acceptedId != 0) {
            parentId = acceptedId;
            isSynonym = true;
        }
        if (authorship == null)
            authorship = "";
        if (isSynonym) {
            listAdapter.add("(" + epithet + " " + authorship + ")");
        } else {
            listAdapter.add(epithet + " " + authorship);
        }
        return parentId;
    }

    void lookupName(String s) {
        listAdapter.clear();
        if(s == null || s.length() == 0) {
            listAdapter.add("empty lookup");
            return;
        }
        try {
            TaxonomyDatabase db = new TaxonomyDatabase(getContext());
            if (recomputeMetaphone) {
                String filename = new File(getActivity().getExternalFilesDir(null), "metaphone.sql").getAbsolutePath();
                db.updateMetaphone(filename);
                recomputeMetaphone = false;
            }
            Cursor cr = db.getMatchingGenera(s);

            while (cr.moveToNext()) {
                Cursor cr2;
                //noinspection StatementWithEmptyBody
                for (Integer parentId = addLineFromCursor(cr);
                     parentId != 0;
                     cr2 = db.getParentTaxon(parentId), parentId = addLineFromCursor(cr2));
                listAdapter.add("");
            }
        } catch (Exception e) {
            listAdapter.clear();
            listAdapter.add(e.getClass().getSimpleName());
            listAdapter.add(e.getLocalizedMessage());
        }
    }

    @Override
    public void setArguments(Bundle b) {
        if (taxonomySearch != null)
            taxonomySearch.setText(b.getString(GENUS, ""));
        lookupName(b.getString(GENUS, ""));
    }
}

class TaxonomyDatabase extends SQLiteAssetHelper {

    private static final String DATABASE_NAME = "taxonomy.db";
    private static final int DATABASE_VERSION = 10;

    TaxonomyDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setForcedUpgrade();
    }

    Cursor getMatchingGenera(String s) {
        String field;
        if(s.toUpperCase().equals(s)) {
            field = "metaphone";
            s = shorten(s);
        } else {
            field = "epithet";
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        for (int rank = 5; rank >= 0; rank--) {
            c = db.rawQuery(
                    "select epithet, authorship, accepted_id, parent_id " +
                            "from taxon " +
                            "where rank = ? and " + field + " like ? " +
                            "order by epithet",
                    new String[]{Integer.toString(rank), s + "%"});
            if (c.getCount() != 0)
                return c;
        }
        return c;
    }

    /**
     * return a shortened, simplified, typo-tolerant name, corresponding to the given epithet
     * @param epithet the epithet to simplify
     * @return the typo-tolerant equivalent
     */
    private String shorten(String epithet) {
        epithet = epithet.toLowerCase();  // ignore case
        epithet = epithet.replaceAll("-", "");  // remove hyphen
        epithet = epithet.replaceAll("c([yie])", "z$1");  // palatal c sounds like z
        epithet = epithet.replaceAll("g([ie])", "j$1");  // palatal g sounds like j
        epithet = epithet.replaceAll("ph", "f");  // ph sounds like f
        epithet = epithet.replaceAll("v", "f");  // v sounds like f // fricative (voiced or not)

        epithet = epithet.replaceAll("h", "");  // h sounds like nothing
        epithet = epithet.replaceAll("[gcq]", "k");  // g, c, q sound like k // guttural
        epithet = epithet.replaceAll("[xz]", "s");  // x, z sound like s
        epithet = epithet.replaceAll("ae", "e");  // ae sounds like e
        epithet = epithet.replaceAll("[ye]", "i");  // y, e sound like i
        epithet = epithet.replaceAll("[ou]", "u");  // o, u sound like u // so we only have a, i, u
        epithet = epithet.replaceAll("[aiu]([aiu])[aiu]*", "$1");  // remove diphtongs
        epithet = epithet.replaceAll("(.)\\1", "$1");  // doubled letters sound like single
        return epithet;
    }

    Cursor getParentTaxon(Integer lookupId) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT epithet, authorship, accepted_id, parent_id " +
                "FROM taxon " +
                "WHERE id=?";
        Cursor c = db.rawQuery(query,
                new String[] {lookupId.toString()});

        c.moveToFirst();
        return c;
    }

    void updateMetaphone(String dst) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        Cursor c1 = db.rawQuery("select id, epithet from taxon", null);
        PrintWriter out = null;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(dst, true)));
            out.println("begin transaction;");
            while (c1.moveToNext()) {
                Integer id = c1.getInt(0);
                String epithet = c1.getString(1);
                String metaphone = shorten(epithet);
                out.println(String.format("updateView taxon set metaphone='%s' where id=%s;", metaphone, id));
            }
            out.println("commit;");
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            if(out != null){
                out.close();
            }
        }
        c1.close();
        db.endTransaction();
        db.close();
    }
}

