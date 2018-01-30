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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.TextView;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

/**
 * Created by mario on 2018-01-30.
 */

public class TaxonomyFragment extends android.support.v4.app.Fragment {

    List<String> stringList;  // think of it as the model.
    ArrayAdapter<String> listAdapter;  // and this would be the presenter

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

        EditText taxonomySearch = rootView.findViewById(R.id.taxonomy_search);
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
        try {
            TaxonomyDatabase db = new TaxonomyDatabase(getContext());
            Cursor cr = db.getMatchingGenera(s);

            while (cr.moveToNext()) {
                Integer parentId = addLineFromCursor(cr);
                //noinspection StatementWithEmptyBody
                for (Cursor cr2;
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
}

class TaxonomyDatabase extends SQLiteAssetHelper {

    private static final String DATABASE_NAME = "taxonomy.db";
    private static final int DATABASE_VERSION = 1;

    TaxonomyDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    Cursor getMatchingGenera(String s) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "select epithet, authorship, accepted_id, parent_id " +
                        "from taxon " +
                        "where rank = 5 and epithet like ? " +
                        "order by epithet",
                new String[] {s + "%"});
        return c;
    }

    Cursor getParentTaxon(Integer lookupId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "select epithet, authorship, accepted_id, parent_id " +
                        "from taxon " +
                        "where id =  ?",
                new String[] {lookupId.toString()});

        c.moveToFirst();
        return c;
    }

}
